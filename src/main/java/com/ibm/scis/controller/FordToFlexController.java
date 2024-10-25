package com.ibm.scis.controller;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import com.ibm.scis.exception.ProcessingException;
import com.ibm.scis.model.ConversionResponse;
import com.ibm.scis.service.FordToFlexService;

@RestController
public class FordToFlexController {
	
	private static final Logger logger = LoggerFactory.getLogger(FordToFlexController.class);
	
	private final String baseUrl = "http://localhost:8080";
	private final RestTemplate restTemplate = new RestTemplate();

	@Autowired
	private FordToFlexService fordToFlexService;

	@GetMapping("/fetch-and-convert")
	public ResponseEntity<ConversionResponse> fetchAndConvertJson(@RequestParam String jsonFileName,
			@RequestParam String csvFilePath) throws IOException, ProcessingException {
		logger.info("Received request to convert JSON to CSV. jsonFileName: {}, csvFilePath: {}", jsonFileName,
				csvFilePath);
		ConversionResponse response;
		if (jsonFileName.contains("pcf_gec")) {
			logger.info("Routing to fordToFlex for pcf_gec.json");
			response = fordToFlexService.fetchAndConvertJson(jsonFileName, null);
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

	@Scheduled(fixedRate = 60000) // 1 hour in milliseconds
	public void scheduledFetchAndConvert() {
		logger.info("Scheduled task started 2");
		String jsonFileName = "pcf_gec";
		String csvFilePath = "output/";

		try {
			String url = String.format("%s/fetch-and-convert?jsonFileName=%s&csvFilePath=%s", baseUrl, jsonFileName,
					csvFilePath);
			logger.info("Scheduled task started. URL: {}", url);

			ResponseEntity<ConversionResponse> response = restTemplate.getForEntity(url, ConversionResponse.class);

			if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null
					&& response.getBody().isSuccess()) {
				logger.info("Scheduled conversion successful. Response: {}", response.getBody());
			} else {
				logger.error("Scheduled conversion failed. Status Code: {}, Message: {}", response.getStatusCode(),
						response.getBody() != null ? response.getBody().getMessage() : "Unknown error");
			}
		} catch (Exception e) {
			logger.error("Error during scheduled conversion: {}", e.getMessage(), e);
		}
	}

}
