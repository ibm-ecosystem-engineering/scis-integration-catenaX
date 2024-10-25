package com.ibm.scis.serviceImpl;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import com.ibm.scis.model.ProductSupplierDTO;
import com.ibm.scis.service.ProductSupplierService;
import com.ibm.scis.utils.ServiceUtil;

@Service
public class ProductSupplierServiceImpl implements ProductSupplierService {

	private final ObjectMapper objectMapper;
	private final Validator validator;
	private final AmazonS3 cosClient;
	private static final Logger logger = LoggerFactory.getLogger(ProductSupplierServiceImpl.class);

	@Autowired
	private ServiceUtil serviceUtil;

	@Value("${ibm.cos.bucket.flex}")
	private String bucketName;

	public ProductSupplierServiceImpl(ObjectMapper objectMapper, AmazonS3 cosClient) {
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

			S3Object s3Object = serviceUtil.fetchJsonFromCos(cosClient, bucketName, "" + jsonFileName);
			InputStream cosJsonInputStream = s3Object.getObjectContent();
			String jsonData = serviceUtil.readJsonData(cosJsonInputStream);

			JsonNode jsonTree;
			try {
				jsonTree = objectMapper.readTree(jsonData);
			} catch (IOException e) {
				logger.error("Failed to parse JSON data: {}", jsonData, e);
				moveToDeadFolder("/import/products//ProductSupplier_20241008102932104.json");
				return new ConversionResponse(false, "Failed to parse JSON data: " + e.getMessage());
			}

			if (!jsonTree.isArray()) {
				logger.error("JSON input is not an array: {}", jsonData);
				moveToDeadFolder("/import/products//ProductSupplier_20241008102932104.json");
				return new ConversionResponse(false, "JSON input is not an array.");
			}

			for (JsonNode node : jsonTree) {
				try {
					ProductSupplierDTO productSupplierDTO = createProductSupplierRecordDTO(node);
					validateDTO(productSupplierDTO);
				} catch (ProcessingException e) {

					logger.error("Invalid JSON node: {}", node.toString());
					moveToDeadFolder("/import/products//ProductSupplier_20241008102932104.json");
					return new ConversionResponse(false, "Invalid JSON data: " + e.getMessage());
				}
			}

			convertToProductMappingCsvAndUpload(jsonTree);
			return new ConversionResponse(true, "Conversion successful. CSV uploaded");
		} catch (IOException e) {
			logger.error("IOException occurred: ", e);
			moveToDeadFolder("/import/products//ProductSupplier_20241008102932104.json");
			return new ConversionResponse(false, "IOException occurred: " + e.getMessage());
		}
	}

	private ProductSupplierDTO createProductSupplierRecordDTO(JsonNode node)
			throws ProcessingException, JsonProcessingException, IllegalArgumentException {
		ProductSupplierDTO productSupplierDTO = new ProductSupplierDTO();
		productSupplierDTO.setMaterialNumber(getNodeText(node, "product.partNumber", true));
		String description = getNodeText(node, "product.description", true);
		if (description != null) {
			description = description.replace("\u00A0", " ");
		}
		
//		productSupplierDTO.setDescription(description);
//		String bpn = getNodeText(node, "supplier.customAttributes.BPNString", true);
//		if (bpn == null || bpn.isEmpty()) {
//			bpn = "BPNL000000000OOS";
//		}
//		productSupplierDTO.setBpn(bpn);
//		String type = getNodeText(node, "supplier.type", true);
//		if (type == null || type.isEmpty() || (!type.equals("supplier") && !type.equals("customer"))) {
//			type = "supplier";
//		}
		String type ="customer";
		String bpn="BPNL000000000OOS";
		productSupplierDTO.setBpn(bpn);
		productSupplierDTO.setType(type);
		return productSupplierDTO;
	}

	private void validateDTO(ProductSupplierDTO productSupplierDTO) throws ProcessingException {
		Set<ConstraintViolation<ProductSupplierDTO>> violations = validator.validate(productSupplierDTO);
		if (!violations.isEmpty()) {
			StringBuilder errorMessage = new StringBuilder("Validation errors: ");
			for (ConstraintViolation<ProductSupplierDTO> violation : violations) {
				errorMessage.append(violation.getMessage()).append("; ");
			}
			throw new ProcessingException(errorMessage.toString());
		}
	}

	private void convertToProductMappingCsvAndUpload(JsonNode jsonTree)
			throws IOException, IllegalArgumentException, ProcessingException {

		LocalDateTime now = LocalDateTime.now();
		String timestamp = serviceUtil.formatDateTime(now);

		String fileName = "Product_Mapping.csv";

		File tempCsvFile = serviceUtil.createTempFile("Product_", ".csv");

		try (FileWriter writer = new FileWriter(tempCsvFile);
				CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader("product_material_number",
						"partner_material_number", "partner_material_description", "bpn", "type"))) {
			for (JsonNode node : jsonTree) {
				ProductSupplierDTO productSupplierDTO = createProductSupplierRecordDTO(node);
				csvPrinter.printRecord(convertNullToString(productSupplierDTO.getMaterialNumber()),
						convertNullToString(productSupplierDTO.getMaterialNumber()),
						convertNullToString(productSupplierDTO.getDescription()),
						convertNullToString(productSupplierDTO.getBpn()),
						convertNullToString(productSupplierDTO.getType()));
			}
		}

		String cosKey = "import/partners/" + fileName;
		serviceUtil.uploadFileToCos(cosClient, bucketName, cosKey, tempCsvFile);

		Path resourcesDir = Paths.get("src", "main", "resources", "import", "partners");
		if (!Files.exists(resourcesDir)) {
			Files.createDirectories(resourcesDir);
		}
		Path destinationFile = resourcesDir.resolve(fileName);
		Files.copy(tempCsvFile.toPath(), destinationFile, StandardCopyOption.REPLACE_EXISTING);

		tempCsvFile.delete();
	}

	private String convertNullToString(String value) {
		return value == null ? "null" : value;
	}

	private String getNodeText(JsonNode node, String fieldName, boolean returnNullIfMissing) {
		JsonNode fieldNode = node.at("/" + fieldName.replace(".", "/"));
		if (fieldNode != null && !fieldNode.isMissingNode()) {
			return fieldNode.asText();
		} else {
			return returnNullIfMissing ? null : "";
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
