package com.ibm.scis.controller;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.ibm.scis.service.COSService;

@RestController
public class CosBucketController {

	@Autowired
	private COSService cosService;

	@GetMapping("/get-all-data/{bucketName}")
	public List<String> getAllData(@PathVariable String bucketName) {
	    return cosService.getAllDataFromBucket(bucketName);
	}

	@DeleteMapping("/delete-folder/{bucketName}/{encodedFolderName}")
	public ResponseEntity<String> deleteContactCsvFolder(@PathVariable String bucketName, @PathVariable String encodedFolderName) {
	    try {
	        String folderName = URLDecoder.decode(encodedFolderName, StandardCharsets.UTF_8.name());
	        cosService.deleteFolder(bucketName, folderName);
	        return ResponseEntity.ok(folderName + " folder deleted successfully from bucket " + bucketName + ".");
	    } catch (Exception e) {
	        return ResponseEntity.status(500).body("Failed to delete " + encodedFolderName + " folder from bucket " + bucketName + ": " + e.getMessage());
	    }
	}

	 @GetMapping("/count-files-in-folders/{bucketName}")
	    public ResponseEntity<Map<String, List<Map<String, Object>>>> countFilesInFolders(@PathVariable String bucketName) {
	        List<String> folderKeys = Arrays.asList("import/contacts/", "upload/pcf-standard/", "upload/pcf/",
	                "import/partners/", "import/products/", "received/", "received/transformed/");
	        
	        Map<String, List<Map<String, Object>>> folderFiles = cosService.getFilesInFolders(bucketName, folderKeys);
	        
	        return ResponseEntity.ok(folderFiles);
	    }

	@GetMapping("/list-all-folders")
	public ResponseEntity<List<String>> listAllFolders() {
		List<String> folders = cosService.listAllFolders();
		return ResponseEntity.ok(folders);
	}

	@GetMapping("/json/{jsonFileName}")
	public ResponseEntity<String> getComplianceJsonData(@PathVariable String jsonFileName) {
		try {
			String jsonData = cosService.getComplianceJsonData(jsonFileName);
			return ResponseEntity.ok(jsonData);
		} catch (Exception e) {
			return ResponseEntity.status(500).body("Error fetching JSON data: " + e.getMessage());
		}
	}

}
