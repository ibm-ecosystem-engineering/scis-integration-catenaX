package com.ibm.scis.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface COSService {

	public Map<String, List<Map<String, Object>>> getFilesInFolders(List<String> folderKeys);

	public List<String> listAllFolders();

	public String getComplianceJsonData(String jsonFileName) throws IOException;

	public List<String> listFiles();

	public Set<String> checkRequiredJsonFiles();

	public List<String> getAllDataFromBucket(String bucketName);

	public void deleteFolder(String bucketName, String folderName);

	public Map<String, List<Map<String, Object>>> getFilesInFolders(String bucketName, List<String> folderKeys);
}
