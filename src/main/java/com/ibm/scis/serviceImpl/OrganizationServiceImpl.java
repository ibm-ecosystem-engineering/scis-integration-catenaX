package com.ibm.scis.serviceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.model.ListObjectsV2Request;
import com.ibm.cloud.objectstorage.services.s3.model.ListObjectsV2Result;
import com.ibm.cloud.objectstorage.services.s3.model.ObjectMetadata;
import com.ibm.cloud.objectstorage.services.s3.model.S3Object;
import com.ibm.cloud.objectstorage.services.s3.model.S3ObjectInputStream;
import com.ibm.cloud.objectstorage.services.s3.model.S3ObjectSummary;
import com.ibm.scis.exception.ProcessingException;
import com.ibm.scis.model.ConversionResponse;
import com.ibm.scis.model.OrganizationRecordDTO;
import com.ibm.scis.service.OrganizationService;
import com.ibm.scis.utils.ServiceUtil;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class OrganizationServiceImpl implements OrganizationService {

	private static final Logger logger = LoggerFactory.getLogger(OrganizationServiceImpl.class);

	private final ObjectMapper objectMapper;
	private final Validator validator;
	private final AmazonS3 cosClient;

	@Autowired
	private ServiceUtil serviceUtil;

	@Value("${ibm.cos.bucket.flex}")
	private String bucketName;

	public OrganizationServiceImpl(ObjectMapper objectMapper, AmazonS3 cosClient) {
		this.objectMapper = objectMapper;
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		this.validator = factory.getValidator();
		this.cosClient = cosClient;
	}

	@Override
	public ConversionResponse convertJsonToCsv(String jsonFileName, File outputDir)
			throws IOException, ProcessingException {
		logger.info("Starting JSON to CSV conversion for file: {}", jsonFileName);

		try {

			S3Object s3Object = serviceUtil.fetchJsonFromCos(cosClient, bucketName, "/import/contacts/" + jsonFileName);
			InputStream cosJsonInputStream = s3Object.getObjectContent();
			String jsonData = serviceUtil.readJsonData(cosJsonInputStream);

			JsonNode jsonTree;
			try {
				jsonTree = objectMapper.readTree(jsonData);
			} catch (IOException e) {
				logger.error("Failed to parse JSON data: {}", jsonData, e);
				moveToDeadFolder("/import/contacts/Organization.json");
				return new ConversionResponse(false, "Failed to parse JSON data: " + e.getMessage());
			}

			if (!jsonTree.isArray()) {
				logger.error("JSON input is not an array: {}", jsonData);
				moveToDeadFolder("/import/contacts/Organization.json");
				return new ConversionResponse(false, "JSON input is not an array.");
			}

			for (JsonNode node : jsonTree) {
				try {
					OrganizationRecordDTO organizationRecordDTO = createOrganizationRecordDTO(node);
					validateDTO(organizationRecordDTO);
				} catch (ProcessingException e) {

					logger.error("Invalid JSON node: {}", node.toString());
					moveToDeadFolder("/import/contacts/Organization.json");
					return new ConversionResponse(false, "Invalid JSON data: " + e.getMessage());
				}
			}

			return convertToOrganizationCsvAndUpload(jsonTree);
		} catch (IOException e) {
			logger.error("IOException occurred: ", e);
			moveToDeadFolder("/import/contacts/Organization.json");
			return new ConversionResponse(false, "IOException occurred: " + e.getMessage());
		}
	}

	private OrganizationRecordDTO createOrganizationRecordDTO(JsonNode node) throws ProcessingException {
		OrganizationRecordDTO organizationRecordDTO = new OrganizationRecordDTO();

		JsonNode customAttributesNode = node.get("customAttributes");
		if (customAttributesNode == null || customAttributesNode.isNull()) {
			customAttributesNode = objectMapper.createObjectNode(); // Create an empty node if null
		}

		String bpn = convertBpn(serviceUtil.getNodeText(customAttributesNode, "BPNString"));
		organizationRecordDTO.setBpn(bpn);
		organizationRecordDTO.setIdentifier("urn:bpn:" + bpn);
		return organizationRecordDTO;
	}

	private void validateDTO(OrganizationRecordDTO organizationRecordDTO) throws ProcessingException {
		Set<ConstraintViolation<OrganizationRecordDTO>> violations = validator.validate(organizationRecordDTO);
		if (!violations.isEmpty()) {
			StringBuilder errorMessage = new StringBuilder("Validation errors: ");
			for (ConstraintViolation<OrganizationRecordDTO> violation : violations) {
				errorMessage.append(violation.getMessage()).append("; ");
			}
			logger.error("Validation failed for DTO: {}", organizationRecordDTO);
			throw new ProcessingException(errorMessage.toString());
		}

		if (organizationRecordDTO.getBpn() == null || organizationRecordDTO.getBpn().isEmpty()) {
			throw new ProcessingException("Mandatory field 'bpn' is missing.");
		}
		if (organizationRecordDTO.getIdentifier() == null || organizationRecordDTO.getIdentifier().isEmpty()) {
			throw new ProcessingException("Mandatory field 'identifier' is missing.");
		}
	}

	private ConversionResponse convertToOrganizationCsvAndUpload(JsonNode jsonTree) throws IOException {
		LocalDateTime now = LocalDateTime.now();
		String timestamp = serviceUtil.formatDateTime(now);

		String fileName = "Contact_ID.csv";

		File tempCsvFile = serviceUtil.createTempFile("Contact_ID", ".csv");

		try (FileWriter writer = new FileWriter(tempCsvFile);
				CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader("bpn", "identifier"))) {
			for (JsonNode node : jsonTree) {
				String bpn = serviceUtil.getNodeText(node, "customAttributes.BPNString");
				String identifier = "";

				if ("Flex".equals(serviceUtil.getNodeText(node, "organizationIdentifier"))) {
					bpn = "BPNL000000000NPH";
					identifier = "urn:bpn:BPNL000000000NPH";
				} else {
					if (bpn != null && !bpn.isEmpty()) {
						identifier = "urn:bpn:" + bpn;
					}
				}

				csvPrinter.printRecord(bpn, identifier);
			}
		}

		String cosKey = "import/contacts/" + fileName;
		serviceUtil.uploadFileToCos(cosClient, bucketName, cosKey, tempCsvFile);
		Path resourcesDir = Paths.get("src", "main", "resources", "import", "partners");
		if (!Files.exists(resourcesDir)) {
			Files.createDirectories(resourcesDir);
		}
		Path destinationFile = resourcesDir.resolve(fileName);
		Files.copy(tempCsvFile.toPath(), destinationFile, StandardCopyOption.REPLACE_EXISTING);

		tempCsvFile.delete();

		return new ConversionResponse(true, "Conversion successful. CSV uploaded");
	}

	private String convertBpn(String value) {
		if (value == null || value.trim().isEmpty()) {
			return "BPNL000000000NPH";
		} else {
			return value;
		}
	}

	private void moveToDeadFolder(String jsonFileName) {
		try {

			logger.info("Bucket name: {}", bucketName);
			logger.info("Checking existence of file: {}", jsonFileName);

			ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucketName);
			ListObjectsV2Result result;
			boolean fileExists = false;
			do {
				result = cosClient.listObjectsV2(req);
				for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
					logger.info("Found object: {}", objectSummary.getKey());
					if (objectSummary.getKey().equals(jsonFileName)) {
						fileExists = true;
					}
				}
				req.setContinuationToken(result.getNextContinuationToken());
			} while (result.isTruncated());

			if (!fileExists) {
				logger.error("File does not exist: {}", jsonFileName);
				return;
			}

			S3Object s3Object = cosClient.getObject(bucketName, jsonFileName);
			S3ObjectInputStream inputStream = s3Object.getObjectContent();
			String jsonData = new BufferedReader(new InputStreamReader(inputStream)).lines()
					.collect(Collectors.joining("\n"));

			String deadFolderKey = "dead-files/" + Paths.get(jsonFileName).getFileName().toString();

			logger.info("Dead folder key: {}", deadFolderKey);

			ObjectMetadata metadata = new ObjectMetadata();
			metadata.setContentLength(jsonData.length());
			cosClient.putObject(bucketName, deadFolderKey, new ByteArrayInputStream(jsonData.getBytes()), metadata);

			logger.info("Moved file to dead folder: {}", deadFolderKey);
		} catch (Exception e) {
			logger.error("Failed to move file to dead folder: {}", jsonFileName, e);
		}
	}

}