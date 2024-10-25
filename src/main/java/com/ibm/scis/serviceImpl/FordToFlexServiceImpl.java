package com.ibm.scis.serviceImpl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.model.AmazonS3Exception;
import com.ibm.cloud.objectstorage.services.s3.model.ListObjectsV2Request;
import com.ibm.cloud.objectstorage.services.s3.model.ListObjectsV2Result;
import com.ibm.cloud.objectstorage.services.s3.model.PutObjectRequest;
import com.ibm.cloud.objectstorage.services.s3.model.S3Object;
import com.ibm.cloud.objectstorage.services.s3.model.S3ObjectSummary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ibm.scis.exception.ProcessingException;
import com.ibm.scis.model.ConversionResponse;
import com.ibm.scis.service.FordToFlexService;

@Service
public class FordToFlexServiceImpl implements FordToFlexService {

	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
	private final AmazonS3 cosClient;
	private static final Logger logger = LoggerFactory.getLogger(FordToFlexServiceImpl.class);
	private final ObjectMapper objectMapper;

	@Value("${ibm.cos.bucket.ford}")
	private String bucketName;

	public FordToFlexServiceImpl(ObjectMapper objectMapper, AmazonS3 cosClient) {
		this.objectMapper = objectMapper;
		this.cosClient = cosClient;
	}

	@Override
	public ConversionResponse fetchAndConvertJson(String jsonFileNamePrefix, File outputDir)
			throws IOException, ProcessingException {
		logger.info("Starting JSON to JSON conversion for file: {}", jsonFileNamePrefix);
		File tempJsonFile = null;
		try {
			String fullFileName = fetchFullFileNameFromCos(jsonFileNamePrefix);
			S3Object s3Object = cosClient.getObject(bucketName, fullFileName);
			InputStream cosJsonInputStream = s3Object.getObjectContent();
			Map<String, Object> inputJson = parseJsonInput(cosJsonInputStream);
			Map<String, Object> outputJson = processJsonObject(inputJson);
			tempJsonFile = writeJsonToTempFile(outputJson);
			uploadJsonToCos(tempJsonFile, fullFileName);
			return new ConversionResponse(true, "Conversion successful. JSON uploaded");
		} catch (IOException e) {
			logger.error("IOException occurred: ", e);
			return new ConversionResponse(false, "IOException occurred: " + e.getMessage());
		} finally {
			if (tempJsonFile != null && tempJsonFile.exists()) {
				tempJsonFile.delete();
			}
		}
	}

	private String fetchFullFileNameFromCos(String jsonFileNamePrefix) throws AmazonS3Exception {
		try {

			ListObjectsV2Request listObjectsV2Request = new ListObjectsV2Request().withBucketName(bucketName)
					.withPrefix("received/" + jsonFileNamePrefix);
			ListObjectsV2Result result = cosClient.listObjectsV2(listObjectsV2Request);

			for (S3ObjectSummary objectSummary : result.getObjectSummaries()) {
				if (objectSummary.getKey().startsWith("received/" + jsonFileNamePrefix)) {
					return objectSummary.getKey();
				}
			}

			logger.error("File not found in COS bucket with prefix: {}", jsonFileNamePrefix);
			throw new AmazonS3Exception("File not found in COS bucket with prefix: " + jsonFileNamePrefix);
		} catch (AmazonS3Exception e) {
			if (e.getStatusCode() == 404) {
				logger.error("File not found in COS bucket with prefix: {}", jsonFileNamePrefix);
				throw new AmazonS3Exception("File not found in COS bucket with prefix: " + jsonFileNamePrefix);
			} else {
				throw e;
			}
		}
	}

	private Map<String, Object> parseJsonInput(InputStream cosJsonInputStream) throws IOException {
		try {
			return objectMapper.readValue(cosJsonInputStream, Map.class);
		} catch (IOException e) {
			logger.error("Failed to parse JSON data: {}", e.getMessage(), e);
			throw e;
		}
	}

	private Map<String, Object> processJsonObject(Map<String, Object> inputJson) {
		Map<String, Object> outputJson = new HashMap<>();
		Map<String, Object> pcf = (Map<String, Object>) inputJson.get("pcf");
		Map<String, Object> nestedPcf = (Map<String, Object>) pcf.get("pcf");

		List<Map<String, Object>> additionalInfo = mapAdditionalInfo(inputJson, pcf, nestedPcf);
		outputJson.put("additionalInfo", additionalInfo);
		outputJson.put("id", pcf.get("id"));
		outputJson.put("createdDate", formatDateTime(pcf.get("created")));
		outputJson.put("status", pcf.get("extWBCSD_pfStatus"));
		outputJson.put("validFrom", formatDateTime(pcf.get("validityPeriodStart")));
		outputJson.put("validTo", formatDateTime(pcf.get("validityPeriodEnd"), true));
		outputJson.put("scorePrimaryDataRatio", nestedPcf.get("primaryDataShare"));

		Map<String, Object> product = mapProduct(inputJson, nestedPcf);
		outputJson.put("product", product);

		Map<String, Object> location = mapLocation(nestedPcf);
		outputJson.put("location", location);

		Map<String, Object> supplier = mapSupplier(inputJson);
		outputJson.put("supplier", supplier);

		return outputJson;
	}

	private List<Map<String, Object>> mapAdditionalInfo(Map<String, Object> inputJson, Map<String, Object> pcf,
			Map<String, Object> nestedPcf) {
		List<Map<String, Object>> additionalInfo = new ArrayList<>();
		additionalInfo.add(createAdditionalInfo("precedingPCFString", inputJson.get("precedingPfIds")));
		additionalInfo.add(createAdditionalInfo("specVersion", pcf.get("specVersion")));
		additionalInfo.add(createAdditionalInfo("partialfullpcf", pcf.get("partialFullPcf")));
		additionalInfo.add(createAdditionalInfo("version", pcf.get("version")));
		additionalInfo.add(createAdditionalInfo("commentString", inputJson.get("comment")));
		additionalInfo.add(createAdditionalInfo("pcfLegalString", inputJson.get("pcfLegalStatement")));
		additionalInfo.add(createAdditionalInfo("declaredUnit", nestedPcf.get("declaredUnit")));
		additionalInfo.add(createAdditionalInfo("unitaryProductAmount", nestedPcf.get("unitaryProductAmount")));
		additionalInfo.add(createAdditionalInfo("exemptedEmissionsPercent", nestedPcf.get("exemptedEmissionsPercent")));
		additionalInfo.add(
				createAdditionalInfo("exemptedEmissionsDescription", nestedPcf.get("exemptedEmissionsDescription")));
		additionalInfo.add(createAdditionalInfo("packagingEmissionsIncluded",
				nestedPcf.get("extWBCSD_packagingEmissionsIncluded")));
		additionalInfo
				.add(createAdditionalInfo("countrySubDivisionString", nestedPcf.get("geographyCountrySubdivision")));
		additionalInfo
				.add(createAdditionalInfo("boundaryProcessDescription", nestedPcf.get("boundaryProcessesDescription")));
		additionalInfo.add(createAdditionalInfo("referencePeriodStart",
				referenceformatDateTime(nestedPcf.get("referencePeriodStart"))));
		additionalInfo.add(createAdditionalInfo("referencePeriodEnd",
				referenceformatDateTime(nestedPcf.get("referencePeriodEnd"))));
		additionalInfo.add(
				createAdditionalInfo("characterizationFactors", nestedPcf.get("extWBCSD_characterizationFactors")));
		additionalInfo.add(createAdditionalInfo("allocationRulesDescription",
				nestedPcf.get("extWBCSD_allocationRulesDescription")));
		additionalInfo.add(createAdditionalInfo("allocationWasteIncineration",
				nestedPcf.get("extTFS_allocationWasteIncineration")));
		additionalInfo.add(createAdditionalInfo("coveragePercent",
				((Map<String, Object>) nestedPcf.get("dataQualityRating")).get("coveragePercent")));
		additionalInfo.add(createAdditionalInfo("technologicalDQR",
				((Map<String, Object>) nestedPcf.get("dataQualityRating")).get("technologicalDQR")));
		additionalInfo.add(createAdditionalInfo("temporalDQR",
				((Map<String, Object>) nestedPcf.get("dataQualityRating")).get("temporalDQR")));
		additionalInfo.add(createAdditionalInfo("geographicalDQR",
				((Map<String, Object>) nestedPcf.get("dataQualityRating")).get("geographicalDQR")));
		additionalInfo.add(createAdditionalInfo("completenessDQR",
				((Map<String, Object>) nestedPcf.get("dataQualityRating")).get("completenessDQR")));
		additionalInfo.add(createAdditionalInfo("reliabilityDQR",
				((Map<String, Object>) nestedPcf.get("dataQualityRating")).get("reliabilityDQR")));
		additionalInfo.add(createAdditionalInfo("pcfExcludingBiogenic", nestedPcf.get("pcfExcludingBiogenic")));
		additionalInfo.add(createAdditionalInfo("pcfincludingbiogenic", nestedPcf.get("pcfIncludingBiogenic")));
		additionalInfo.add(createAdditionalInfo("fossilGhgEmissions", nestedPcf.get("fossilGhgEmissions")));
		additionalInfo.add(createAdditionalInfo("biogenicCarbonEmissionsOotherThanCo2",
				nestedPcf.get("biogenicCarbonEmissionsOtherThanCO2")));
		additionalInfo.add(createAdditionalInfo("biogenicCarbonWithdrawal", nestedPcf.get("biogenicCarbonWithdrawal")));
		additionalInfo.add(createAdditionalInfo("dlucGhgEmissions", nestedPcf.get("dlucGhgEmissions")));
		additionalInfo.add(createAdditionalInfo("luGhgEmissions", nestedPcf.get("extTFS_luGhgEmissions")));
		additionalInfo.add(createAdditionalInfo("aircraftGhgEmissions", nestedPcf.get("aircraftGhgEmissions")));
		additionalInfo.add(
				createAdditionalInfo("packagingGhGEmissionsFloat", nestedPcf.get("extWBCSD_packagingGhgEmissions")));
		additionalInfo.add(createAdditionalInfo("nonBiogenicProductCarbonFootprint",
				nestedPcf.get("distributionStagePcfExcludingBiogenic")));
		additionalInfo.add(createAdditionalInfo("distributionPcfIncludingBiogenic",
				nestedPcf.get("distributionStagePcfIncludingBiogenic")));
		additionalInfo.add(createAdditionalInfo("distributionFossilGhgEmissions",
				nestedPcf.get("distributionStageFossilGhgEmissions")));
		additionalInfo.add(createAdditionalInfo("distributionBiogenicCarbonEmissionsOtherThanCO2",
				nestedPcf.get("distributionStageBiogenicCarbonEmissionsOtherThanCO2")));
		additionalInfo.add(createAdditionalInfo("distributionBiogenicCarbonWithdrawal",
				nestedPcf.get("distributionStageBiogenicCarbonWithdrawal")));
		additionalInfo.add(createAdditionalInfo("distributionDlucGhgEmissions",
				nestedPcf.get("extTFS_distributionStageDlucGhgEmissions")));
		additionalInfo.add(createAdditionalInfo("distributionLuGhgEmissions",
				nestedPcf.get("extTFS_distributionStageLuGhgEmissions")));
		additionalInfo.add(createAdditionalInfo("distributionAircraftGhgEmissions",
				nestedPcf.get("distributionStageAircraftGhgEmissions")));
		additionalInfo.add(createAdditionalInfo("carbonContentTotal", nestedPcf.get("carbonContentTotal")));
		additionalInfo.add(createAdditionalInfo("fossilCarbonContent", nestedPcf.get("extWBCSD_fossilCarbonContent")));
		additionalInfo.add(createAdditionalInfo("biogenicCarbonContent", nestedPcf.get("carbonContentBiogenic")));

		List<Map<String, String>> crossSectoralStandardsUsed = (List<Map<String, String>>) nestedPcf
				.get("crossSectoralStandardsUsed");
		if (crossSectoralStandardsUsed != null && !crossSectoralStandardsUsed.isEmpty()) {
			additionalInfo.add(createAdditionalInfo("crossSectoralStandardsUsed",
					crossSectoralStandardsUsed.get(0).get("crossSectoralStandard")));
		} else {
			additionalInfo.add(createAdditionalInfo("crossSectoralStandardsUsed", ""));
		}

		return additionalInfo;
	}

	private Map<String, Object> mapProduct(Map<String, Object> inputJson, Map<String, Object> nestedPcf) {
		Map<String, Object> product = new HashMap<>();
		product.put("partNumber", inputJson.get("materialNumber"));
		product.put("measurementUnit", nestedPcf.get("productMassPerDeclaredUnit"));
		return product;
	}

	private Map<String, Object> mapLocation(Map<String, Object> nestedPcf) {
		Map<String, Object> location = new HashMap<>();
		location.put("geo", nestedPcf.get("geographyRegionOrSubregion"));
		String countryCode = (String) nestedPcf.get("geographyCountry");
		String iso3CountryCode = convertToISO3CountryCode(countryCode);
		location.put("country", iso3CountryCode);
		return location;
	}

	private Map<String, Object> mapSupplier(Map<String, Object> inputJson) {
		Map<String, Object> supplier = new HashMap<>();
		Map<String, Object> supplierCustomAttributes = new HashMap<>();
		supplierCustomAttributes.put("BPNString", inputJson.get("bpn"));
		supplier.put("customAttributes", supplierCustomAttributes);
		return supplier;
	}

	private File writeJsonToTempFile(Map<String, Object> outputJson) throws IOException {
		File tempJsonFile = File.createTempFile("FordToFlex_", ".json");
		try (FileOutputStream fos = new FileOutputStream(tempJsonFile)) {
			objectMapper.writeValue(fos, outputJson);
		}
		return tempJsonFile;
	}

	private void uploadJsonToCos(File outputFile, String jsonFileName) {
		cosClient.putObject(new PutObjectRequest(bucketName, "received/transformed/" + jsonFileName, outputFile));
	}

	private String formatDateTime(Object dateTimeObj) {
		return formatDateTime(dateTimeObj, false);
	}

	private String formatDateTime(Object dateTimeObj, boolean addOneYear) {
		LocalDateTime dateTime;
		if (dateTimeObj == null || (dateTimeObj instanceof String && ((String) dateTimeObj).isEmpty())) {
			dateTime = LocalDateTime.now();
			if (addOneYear) {
				dateTime = dateTime.plusMonths(12);
			}
		} else {
			dateTime = LocalDateTime.parse(dateTimeObj.toString(), DateTimeFormatter.ISO_DATE_TIME);
		}
		return dateTime.format(FORMATTER);
	}

	private String referenceformatDateTime(Object dateTimeObj) {
		if (dateTimeObj == null) {
			return null;
		}
		LocalDateTime dateTime = LocalDateTime.parse(dateTimeObj.toString(), DateTimeFormatter.ISO_DATE_TIME);
		return dateTime.format(FORMATTER);
	}

	private Map<String, Object> createAdditionalInfo(String name, Object value) {
		Map<String, Object> additionalInfo = new HashMap<>();
		additionalInfo.put("name", name);
		additionalInfo.put("value", value);
		return additionalInfo;
	}

	private String convertToISO3CountryCode(String code) {
		if (code.length() == 2) {
			for (Locale locale : Locale.getAvailableLocales()) {
				if (locale.getCountry().equals(code)) {
					return locale.getISO3Country();
				}
			}
		} else if (code.length() == 3) {
			return code;
		}
		return "Invalid country code";
	}

}