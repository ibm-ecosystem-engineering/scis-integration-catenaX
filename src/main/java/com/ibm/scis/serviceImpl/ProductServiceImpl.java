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
import com.ibm.scis.model.ProductRecordDTO;
import com.ibm.scis.service.ProductService;
import com.ibm.scis.utils.ServiceUtil;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

	private static final Logger logger = LoggerFactory.getLogger(ProductServiceImpl.class);
	private final ObjectMapper objectMapper;
	private final Validator validator;
	private final AmazonS3 cosClient;
	private final String currentDate;

	@Autowired
	private ServiceUtil serviceUtil;

	@Value("${ibm.cos.bucket.flex}")
	private String bucketName;

	public ProductServiceImpl(ObjectMapper objectMapper, AmazonS3 cosClient) {
		this.objectMapper = objectMapper;
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		this.validator = factory.getValidator();
		this.cosClient = cosClient;
		this.currentDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS"));
	}

	@Override
	public ConversionResponse convertJsonToCsv(String jsonFileName, File outputDir)
			throws IOException, ProcessingException {
		logger.info("Starting JSON to CSV conversion for file: {}", jsonFileName);

		try {

			S3Object s3Object = serviceUtil.fetchJsonFromCos(cosClient, bucketName,
					"/import/products//" + jsonFileName);
			InputStream cosJsonInputStream = s3Object.getObjectContent();
			String jsonData = serviceUtil.readJsonData(cosJsonInputStream);

			JsonNode jsonTree;
			try {
				jsonTree = objectMapper.readTree(jsonData);
			} catch (IOException e) {
				logger.error("Failed to parse JSON data: {}", jsonData, e);
				moveToDeadFolder("/import/products//product.json");
				return new ConversionResponse(false, "Failed to parse JSON data: " + e.getMessage());
			}

			if (!jsonTree.isArray()) {
				logger.error("JSON input is not an array: {}", jsonData);
				moveToDeadFolder("/import/products//product.json");
				return new ConversionResponse(false, "JSON input is not an array.");
			}

			for (JsonNode node : jsonTree) {
				try {
					ProductRecordDTO productRecordDTO = createProductRecordDTO(node);
					validateDTO(productRecordDTO);
				} catch (ProcessingException e) {

					logger.error("Invalid JSON node: {}", node.toString());
					moveToDeadFolder("/import/products//product.json");
					return new ConversionResponse(false, "Invalid JSON data: " + e.getMessage());
				}
			}

			convertToProductCsvAndUpload(jsonTree);
			convertToProductIdCsvAndUpload(jsonTree);
			return new ConversionResponse(true, "Conversion successful. CSV uploaded");
		} catch (IOException e) {
			logger.error("IOException occurred: ", e);
			moveToDeadFolder("/import/products//product.json");
			return new ConversionResponse(false, "IOException occurred: " + e.getMessage());
		}
	}

	private ProductRecordDTO createProductRecordDTO(JsonNode node) throws ProcessingException {
		ProductRecordDTO productRecordDTO = new ProductRecordDTO();
		productRecordDTO.setPartNumber(serviceUtil.getNodeText(node, "partNumber"));
		String description = serviceUtil.getNodeText(node, "description");
		if (description != null) {
			description = description.replace("\u00A0", " "); // Replace non-breaking spaces with regular spaces
		}
		productRecordDTO.setDescription(description);
		productRecordDTO.setCreateDate(currentDate);
		productRecordDTO.setValidFrom(currentDate);
		productRecordDTO.setValidTo("9999-12-31T11:46:26.996253");
		productRecordDTO.setProductCategory("011-99000");
		return productRecordDTO;
	}

	private void validateDTO(ProductRecordDTO productRecordDTO) throws ProcessingException {
		Set<ConstraintViolation<ProductRecordDTO>> violations = validator.validate(productRecordDTO);
		if (!violations.isEmpty()) {
			StringBuilder errorMessage = new StringBuilder("Validation errors: ");
			for (ConstraintViolation<ProductRecordDTO> violation : violations) {
				errorMessage.append(violation.getMessage()).append("; ");
			}
			throw new ProcessingException(errorMessage.toString());
		}
	}

	private void convertToProductCsvAndUpload(JsonNode jsonTree) throws IOException, ProcessingException {

		LocalDateTime now = LocalDateTime.now();
		String timestamp = serviceUtil.formatDateTime(now);

		String fileName = "Product.csv";

		File tempCsvFile = serviceUtil.createTempFile("Product_", ".csv");

		try (FileWriter writer = new FileWriter(tempCsvFile);
				CSVPrinter csvPrinter = new CSVPrinter(writer,
						CSVFormat.DEFAULT.withHeader("material_number", "material_description", "created", "valid_from",
								"valid_to", "product_category_cpc", "product_name_company"))) {
			for (JsonNode node : jsonTree) {
				ProductRecordDTO productRecordDTO = createProductRecordDTO(node);
				csvPrinter.printRecord(convertNullToString(productRecordDTO.getPartNumber()), // material_number
						convertNullToString(productRecordDTO.getDescription()),
						convertNullToString(productRecordDTO.getCreateDate()),
						convertNullToString(productRecordDTO.getValidFrom()),
						convertNullToString(productRecordDTO.getValidTo()),
						convertNullToString(productRecordDTO.getProductCategory()), // product_category_cpc
						convertNullToString(productRecordDTO.getDescription()) // product_name_company
				);
			}
		}

		String cosKey = "import/products/" + fileName;
		serviceUtil.uploadFileToCos(cosClient, bucketName, cosKey, tempCsvFile);
		Path resourcesDir = Paths.get("src", "main", "resources", "import", "partners");
		if (!Files.exists(resourcesDir)) {
			Files.createDirectories(resourcesDir);
		}
		Path destinationFile = resourcesDir.resolve(fileName);
		Files.copy(tempCsvFile.toPath(), destinationFile, StandardCopyOption.REPLACE_EXISTING);

		tempCsvFile.delete();
	}

	private void convertToProductIdCsvAndUpload(JsonNode jsonTree) throws IOException, ProcessingException {

		LocalDateTime now = LocalDateTime.now();
		String timestamp = serviceUtil.formatDateTime(now);

		String fileName = "Product_ID.csv";

		File tempCsvFile = serviceUtil.createTempFile("Product_ID_", ".csv");

		try (FileWriter writer = new FileWriter(tempCsvFile);
				CSVPrinter csvPrinter = new CSVPrinter(writer,
						CSVFormat.DEFAULT.withHeader("material_number", "identifier"))) {
			for (JsonNode node : jsonTree) {
				ProductRecordDTO productRecordDTO = createProductRecordDTO(node);
				String id = "urn:id:" + productRecordDTO.getPartNumber();

				csvPrinter.printRecord(convertNullToString(productRecordDTO.getPartNumber()), // material_number
						id // identifier
				);
			}
		}

		tempCsvFile.delete();
	}

	private String convertNullToString(String value) {
		return value == null ? "null" : value;
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