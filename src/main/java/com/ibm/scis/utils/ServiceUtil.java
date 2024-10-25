package com.ibm.scis.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.model.AmazonS3Exception;
import com.ibm.cloud.objectstorage.services.s3.model.PutObjectRequest;
import com.ibm.cloud.objectstorage.services.s3.model.S3Object;

@Component
public class ServiceUtil {

	private static final Logger logger = LoggerFactory.getLogger(ServiceUtil.class);
	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS");

	public static S3Object fetchJsonFromCos(AmazonS3 cosClient, String bucketName, String jsonFileName)
			throws AmazonS3Exception {
		try {
			return cosClient.getObject(bucketName, jsonFileName);
		} catch (AmazonS3Exception e) {
			if (e.getStatusCode() == 404) {
				logger.error("File not found in COS bucket: {}", jsonFileName);
				throw new AmazonS3Exception("File not found in COS bucket: " + jsonFileName);
			} else {
				throw e;
			}
		}
	}

	public static List<Map<String, Object>> parseJsonInput(ObjectMapper objectMapper, InputStream cosJsonInputStream)
			throws IOException {
		try {
			return objectMapper.readValue(cosJsonInputStream, List.class);
		} catch (IOException e) {
			logger.error("Failed to parse JSON data: {}", e.getMessage(), e);
			throw e;
		}
	}

	public static File createTempFile(String prefix, String suffix) throws IOException {
		return File.createTempFile(prefix, suffix);
	}

	public static void uploadFileToCos(AmazonS3 cosClient, String bucketName, String cosKey, File file) {
		cosClient.putObject(new PutObjectRequest(bucketName, cosKey, file));
	}

	public static String formatDateTime(LocalDateTime dateTime) {
		return dateTime.format(FORMATTER);
	}

	public static String getNodeText(JsonNode node, String fieldName) {
		JsonNode fieldNode = node.at("/" + fieldName.replace(".", "/"));
		return fieldNode != null && !fieldNode.isMissingNode() ? fieldNode.asText() : "";
	}

	public static String readJsonData(InputStream cosJsonInputStream) throws IOException {
		return new BufferedReader(new InputStreamReader(cosJsonInputStream)).lines().collect(Collectors.joining("\n"));
	}
}
