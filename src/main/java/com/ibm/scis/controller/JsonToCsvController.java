package com.ibm.scis.controller;

import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import com.ibm.scis.exception.ProcessingException;
import com.ibm.scis.model.ConversionResponse;
import com.ibm.scis.service.COSService;
import com.ibm.scis.service.ComplianceRecordService;
import com.ibm.scis.service.OrganizationService;
import com.ibm.scis.service.ProductService;
import com.ibm.scis.service.ProductSupplierService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
public class JsonToCsvController {

	@Value("${conversion.service.base-url}")
	private String baseUrl;

	@Autowired
	private COSService cosService;



	private static final Logger logger = LoggerFactory.getLogger(JsonToCsvController.class);

	@Autowired
	private ProductService productService;

	@Autowired
	private OrganizationService organizationService;

	@Autowired
	private ComplianceRecordService complianceRecordService;

	private final RestTemplate restTemplate = new RestTemplate();

	@Autowired
	private ProductSupplierService productSupplierService;

	private final Map<String, Long> lastProcessedTimes = new HashMap<>();
	private final String stateFilePath = "lastProcessedTimes.ser";

	public JsonToCsvController() {
		loadState();
	}

	@GetMapping("/convert")
	public ResponseEntity<ConversionResponse> convertJsonToCsv(@RequestParam String jsonFileName,
			@RequestParam String csvFilePath) throws IOException, ProcessingException {
		logger.info("Received request to convert JSON to CSV. jsonFileName: {}, csvFilePath: {}", jsonFileName,
				csvFilePath);

		ConversionResponse response;
		if (jsonFileName.equalsIgnoreCase("product.json")) {
			logger.info("Routing to ProductService for product.json");
			response = productService.convertJsonToCsv(jsonFileName, null);
		} else if (jsonFileName.equalsIgnoreCase("Compliance.json")) {
			logger.info("Routing to ComplianceRecordService for compliance.json");
			response = complianceRecordService.convertJsonToCsv(jsonFileName, null);
		} else if (jsonFileName.equalsIgnoreCase("Organization.json")) {
			logger.info("Routing to OrganizationService for Organization.json");
			response = organizationService.convertJsonToCsv(jsonFileName, null);
		} else if (jsonFileName.startsWith("ProductSupplier")) {
			logger.info("Fetching the latest ProductSupplier file");
			String latestProductSupplierFile = getLatestProductSupplierFile();
			if (latestProductSupplierFile == null) {
				logger.error("No ProductSupplier files found");
				return ResponseEntity.status(HttpStatus.BAD_REQUEST)
						.body(new ConversionResponse(false, "No ProductSupplier files found."));
			}
			logger.info("Routing to ProductSupplierService for {}", latestProductSupplierFile);
			response = productSupplierService.convertJsonToCsv(latestProductSupplierFile, null);
		} else {
			logger.error("Invalid JSON file name: {}", jsonFileName);
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(new ConversionResponse(false, "Invalid JSON File."));
		}

		if (response.isSuccess()) {
			logger.info("Conversion successful");
			return ResponseEntity.ok(response);
		} else {
			logger.error("Conversion failed: {}", response.getMessage());
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
		}
	}

	private String getLatestProductSupplierFile() {
		List<String> files = cosService.listFiles();
		logger.info(files.toString() + " files found");
		return files.stream()
				.filter(file -> file.startsWith("/import/products//ProductSupplier_") && file.endsWith(".json"))
				.max(Comparator.comparing(this::extractTimestamp)).orElse(null);
	}

	private long extractTimestamp(String fileName) {
		String timestampStr = fileName.replaceAll("[^0-9]", "");
		return Long.parseLong(timestampStr);
	}

	@GetMapping("/json/check")
	public ResponseEntity<Set<String>> checkRequiredJsonFiles() {
		try {
			Set<String> foundFiles = cosService.checkRequiredJsonFiles();
			return ResponseEntity.ok(foundFiles);
		} catch (Exception e) {
			return ResponseEntity.status(500).body(null);
		}
	}

	@Scheduled(fixedRate = 60000)
	public synchronized void scheduledCheckAndConvert() {
		logger.info("Scheduled task started 1");
		Set<String> foundFiles = cosService.checkRequiredJsonFiles();
		long currentTime = System.currentTimeMillis();
		boolean processedAnyFile = false;

		for (String jsonFilePath : foundFiles) {
			Long lastProcessedTime = lastProcessedTimes.get(jsonFilePath);
			logger.info("Checking file: {}. Last processed time: {}", jsonFilePath, lastProcessedTime);

			if (lastProcessedTime == null || currentTime - lastProcessedTime >= (TimeUnit.MINUTES.toMillis(1))) {
				try {

					String jsonFileName = Paths.get(jsonFilePath).getFileName().toString();
					logger.info("Processing file: {}", jsonFileName);

					String url = String.format("%s/convert?jsonFileName=%s&csvFilePath=output/", baseUrl, jsonFileName);
					logger.info("Calling URL: {}", url);
					ResponseEntity<ConversionResponse> response = restTemplate.getForEntity(url,
							ConversionResponse.class);

					if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null
							&& response.getBody().isSuccess()) {
						lastProcessedTimes.put(jsonFilePath, currentTime);
						logger.info("Successfully processed file: {}. Updated last processed time: {}", jsonFileName,
								currentTime);
						processedAnyFile = true;
					} else {
						logger.error("Failed to process file: {}", jsonFileName);
					}
				} catch (Exception e) {
					logger.error("Error processing file {}: {}", jsonFilePath, e.getMessage());
				}
			} else {
				logger.info("Skipping file: {}. It was processed within the last 1 hours.", jsonFilePath);
			}
		}

		if (!processedAnyFile) {
			logger.info("No files were processed. All files have been processed within the last 1 min.");
		}

		saveState();
		logger.info("Scheduled task completed");
	}

	private void loadState() {
		try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(stateFilePath))) {
			Map<String, Long> savedState = (Map<String, Long>) ois.readObject();
			lastProcessedTimes.putAll(savedState);
			logger.info("Loaded state from file");
		} catch (FileNotFoundException e) {
			logger.info("State file not found, starting with an empty state");
		} catch (IOException | ClassNotFoundException e) {
			logger.error("Error loading state: {}", e.getMessage());
		}
	}

	private void saveState() {
		try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(stateFilePath))) {
			oos.writeObject(lastProcessedTimes);
			logger.info("Saved state to file");
		} catch (IOException e) {
			logger.error("Error saving state: {}", e.getMessage());
		}
	}
}