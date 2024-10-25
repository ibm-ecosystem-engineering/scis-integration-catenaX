package com.ibm.scis.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.model.DeleteObjectsRequest;
import com.ibm.cloud.objectstorage.services.s3.model.ListObjectsV2Request;
import com.ibm.cloud.objectstorage.services.s3.model.ListObjectsV2Result;
import com.ibm.cloud.objectstorage.services.s3.model.ObjectListing;
import com.ibm.cloud.objectstorage.services.s3.model.S3ObjectSummary;
import com.ibm.scis.service.COSService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import com.fasterxml.jackson.databind.JsonNode;
import com.ibm.cloud.objectstorage.services.s3.model.S3Object;
import com.ibm.cloud.objectstorage.services.s3.model.AmazonS3Exception;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class COSServiceImpl implements COSService {

	@Autowired
	private AmazonS3 cosClient;

	@Value("${ibm.cos.bucket.ford}")
	private String bucketName;
	
	@Value("${ibm.cos.bucket.flex}")
	private String bucketNameflex;

	private static final Logger logger = LoggerFactory.getLogger(COSServiceImpl.class);

	@Autowired
	private ObjectMapper objectMapper;
	
	@Override
	public List<String> getAllDataFromBucket(String bucketNameflex) {
	    List<String> dataList = new ArrayList<>();
	    int objectCount = 0;

	    try {
	        ObjectListing objectListing = cosClient.listObjects(bucketNameflex);

	        while (true) {
	            List<S3ObjectSummary> objectSummaries = objectListing.getObjectSummaries();
	            if (objectSummaries == null || objectSummaries.isEmpty()) {
	                System.err.println("No objects found in bucket: " + bucketNameflex);
	                break;
	            }

	            for (S3ObjectSummary os : objectSummaries) {
	                System.out.println("Found object: " + os.getKey());
	                dataList.add(os.getKey());
	                objectCount++;
	            }

	            if (objectListing.isTruncated()) {
	                objectListing = cosClient.listNextBatchOfObjects(objectListing);
	            } else {
	                break;
	            }
	        }
	    } catch (Exception e) {
	        System.err.println("Error listing objects in bucket: " + bucketNameflex);
	        e.printStackTrace();
	    }

	    System.out.println("Total number of objects in bucket: " + objectCount);
	    return dataList;
	}

	

	@Override
	public Map<String, List<Map<String, Object>>> getFilesInFolders(List<String> folderKeys) {
		Map<String, List<Map<String, Object>>> folderFiles = new HashMap<>();

		for (String folderKey : folderKeys) {
			ListObjectsV2Request listObjectsV2Request = new ListObjectsV2Request().withBucketName(bucketNameflex)
					.withPrefix(folderKey).withDelimiter("/");

			ListObjectsV2Result result;
			List<Map<String, Object>> fileInfos = new ArrayList<>();

			do {
				result = cosClient.listObjectsV2(listObjectsV2Request);
				for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
					String fileName = objectSummary.getKey();
					Date creationTime = cosClient.getObjectMetadata(bucketNameflex, fileName).getLastModified();

					Map<String, Object> fileInfo = new HashMap<>();
					fileInfo.put("fileName", fileName);
					fileInfo.put("creationTime", creationTime);

					fileInfos.add(fileInfo);
				}
				listObjectsV2Request.setContinuationToken(result.getNextContinuationToken());
			} while (result.isTruncated());

			folderFiles.put(folderKey, fileInfos);
		}

		return folderFiles;
	}

	@Override
	public List<String> listAllFolders() {
		List<String> folders = new ArrayList<>();
		ListObjectsV2Request listObjectsV2Request = new ListObjectsV2Request().withBucketName(bucketNameflex)
				.withDelimiter("/");

		ListObjectsV2Result result;

		do {
			result = cosClient.listObjectsV2(listObjectsV2Request);
			folders.addAll(result.getCommonPrefixes());
			listObjectsV2Request.setContinuationToken(result.getNextContinuationToken());
		} while (result.isTruncated());

		return folders;
	}

	@Override
	public String getComplianceJsonData(String jsonFileName) throws IOException {
		logger.info("Fetching JSON data from COS bucket for file: {}", jsonFileName);

		S3Object s3Object;
		try {
			s3Object = cosClient.getObject(bucketNameflex, "/import/contacts/" + jsonFileName);
		} catch (AmazonS3Exception e) {
			if (e.getStatusCode() == 404) {
				logger.error("The specified key does not exist in the COS bucket.");
				throw new IOException("The specified key does not exist in the COS bucket.");
			} else {
				throw e;
			}
		}

		InputStream cosJsonInputStream = s3Object.getObjectContent();
		String jsonData = new BufferedReader(new InputStreamReader(cosJsonInputStream)).lines()
				.collect(Collectors.joining("\n"));

		logger.info("Fetched JSON data: {}", jsonData);

		JsonNode jsonTree;
		try {
			jsonTree = objectMapper.readTree(jsonData);
		} catch (IOException e) {
			logger.error("Failed to parse JSON data: {}", jsonData, e);
			throw new IOException("Failed to parse JSON data: " + e.getMessage());
		}

		return jsonTree.toPrettyString();
	}

	@Override
	public List<String> listFiles() {
		List<String> fileList = new ArrayList<>();
		try {
			ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucketNameflex);
			ListObjectsV2Result result;

			do {
				result = cosClient.listObjectsV2(req);
				for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
					fileList.add(objectSummary.getKey());
				}
				req.setContinuationToken(result.getNextContinuationToken());
			} while (result.isTruncated());
		} catch (Exception e) {
			logger.error("Error listing objects in bucket: {}", bucketNameflex, e);
		}
		return fileList;
	}

	public Set<String> checkRequiredJsonFiles() {
		logger.info("Checking for required JSON files in COS bucket");

		Set<String> requiredPrefixes = new HashSet<>();
		requiredPrefixes.add("product");
		requiredPrefixes.add("Compliance");
		requiredPrefixes.add("ProductSupplier_");
		requiredPrefixes.add("Organization");

		Set<String> foundFiles = new HashSet<>();
		ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucketNameflex);
		ListObjectsV2Result result;

		do {
			result = cosClient.listObjectsV2(req);

			for (S3ObjectSummary summary : result.getObjectSummaries()) {
				String key = summary.getKey();

				if (key.startsWith("dead-files/")) {
					continue;
				}
				for (String prefix : requiredPrefixes) {
					if (key.contains(prefix) && key.endsWith(".json")) {
						foundFiles.add(key);
						break;
					}
				}
			}

			req.setContinuationToken(result.getNextContinuationToken());
		} while (result.isTruncated());

		logger.info("Required JSON files found: {}", foundFiles);
		return foundFiles;
	}
	
	
	public void deleteFolder(String bucketNameflex, String folderName) {
	    String folderKey = folderName + "/";

	    ListObjectsV2Request listObjectsV2Request = new ListObjectsV2Request().withBucketName(bucketNameflex)
	            .withPrefix(folderKey);
	    ListObjectsV2Result result;

	    List<DeleteObjectsRequest.KeyVersion> keys = new ArrayList<>();

	    do {
	        result = cosClient.listObjectsV2(listObjectsV2Request);
	        for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
	            keys.add(new DeleteObjectsRequest.KeyVersion(objectSummary.getKey()));
	        }
	        listObjectsV2Request.setContinuationToken(result.getNextContinuationToken());
	    } while (result.isTruncated());

	    if (!keys.isEmpty()) {
	        DeleteObjectsRequest deleteObjectsRequest = new DeleteObjectsRequest(bucketNameflex).withKeys(keys)
	                .withQuiet(true);
	        cosClient.deleteObjects(deleteObjectsRequest);
	    }
	}


    @Override
	 public Map<String, List<Map<String, Object>>> getFilesInFolders(String bucketNameflex, List<String> folderKeys) {
	        Map<String, List<Map<String, Object>>> folderFiles = new HashMap<>();

	        for (String folderKey : folderKeys) {
	            ListObjectsV2Request listObjectsV2Request = new ListObjectsV2Request().withBucketName(bucketNameflex)
	                    .withPrefix(folderKey).withDelimiter("/");

	            ListObjectsV2Result result;
	            List<Map<String, Object>> fileInfos = new ArrayList<>();

	            do {
	                result = cosClient.listObjectsV2(listObjectsV2Request);
	                for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
	                    String fileName = objectSummary.getKey();
	                    Date creationTime = cosClient.getObjectMetadata(bucketNameflex, fileName).getLastModified();

	                    Map<String, Object> fileInfo = new HashMap<>();
	                    fileInfo.put("fileName", fileName);
	                    fileInfo.put("creationTime", creationTime);

	                    fileInfos.add(fileInfo);
	                }
	                listObjectsV2Request.setContinuationToken(result.getNextContinuationToken());
	            } while (result.isTruncated());

	            folderFiles.put(folderKey, fileInfos);
	        }

	        return folderFiles;
	    }


}
