package com.ibm.scis.serviceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibm.scis.model.ComplianceRecordDTO;
import com.ibm.scis.model.ConversionResponse;
import com.ibm.cloud.objectstorage.services.s3.AmazonS3;
import com.ibm.cloud.objectstorage.services.s3.model.S3Object;
import com.ibm.cloud.objectstorage.services.s3.model.S3ObjectInputStream;
import com.ibm.cloud.objectstorage.services.s3.model.S3ObjectSummary;
import com.ibm.scis.exception.ProcessingException;
import com.ibm.scis.service.ComplianceRecordService;
import com.ibm.scis.utils.ServiceUtil;
import com.ibm.cloud.objectstorage.services.s3.model.ListObjectsV2Request;
import com.ibm.cloud.objectstorage.services.s3.model.ListObjectsV2Result;
import com.ibm.cloud.objectstorage.services.s3.model.ObjectMetadata;
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
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ComplianceRecordServiceImpl implements ComplianceRecordService {

	private static final Logger logger = LoggerFactory.getLogger(OrganizationServiceImpl.class);
	private final ObjectMapper objectMapper;
	private final Validator validator;
	private static final String START_TIME_COMPONENT = "T00:00:00.000000";
	private static final String END_TIME_COMPONENT = "T23:59:59.999999";
	private final AmazonS3 cosClient;

	@Autowired
	private ServiceUtil serviceUtil;

	LocalDateTime now = LocalDateTime.now();
	DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");

	@Value("${ibm.cos.bucket.flex}")
	private String bucketName;

	public ComplianceRecordServiceImpl(ObjectMapper objectMapper, AmazonS3 cosClient) {
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

			S3Object s3Object = serviceUtil.fetchJsonFromCos(cosClient, bucketName, "import/" + jsonFileName);
			InputStream cosJsonInputStream = s3Object.getObjectContent();
			String jsonData = serviceUtil.readJsonData(cosJsonInputStream);

			JsonNode jsonTree;
			try {
				jsonTree = objectMapper.readTree(jsonData);
			} catch (IOException e) {
				logger.error("Failed to parse JSON data: {}", jsonData, e);
				moveToDeadFolder("/import/partners/Compliance.json");
				return new ConversionResponse(false, "Failed to parse JSON data: " + e.getMessage());
			}

			if (!jsonTree.isArray()) {
				logger.error("JSON input is not an array: {}", jsonData);
				moveToDeadFolder("/import/partners/Compliance.json");
				return new ConversionResponse(false, "JSON input is not an array.");
			}

			for (JsonNode node : jsonTree) {
				try {
					ComplianceRecordDTO complianceRecordDTO = createComplianceRecordDTO(node);
					validateDTO(complianceRecordDTO);
				} catch (ProcessingException e) {

					logger.error("Invalid JSON node: {}", node.toString());
					moveToDeadFolder("/import/partners/Compliance.json");
					return new ConversionResponse(false, "Invalid JSON data: " + e.getMessage());
				}
			}

			convertToPcfCsv(jsonTree);
			convertToPcfStandardCsv(jsonTree);
			return new ConversionResponse(true, "Conversion successful. CSV uploaded");
		} catch (IOException e) {
			logger.error("IOException occurred: ", e);
			moveToDeadFolder("/import/partners/Compliance.json");
			return new ConversionResponse(false, "IOException occurred: " + e.getMessage());
		}
	}

	private ComplianceRecordDTO createComplianceRecordDTO(JsonNode node) throws ProcessingException {
		ComplianceRecordDTO complianceRecordDTO = new ComplianceRecordDTO();
		complianceRecordDTO.setMaterialNumber(getNodeText(node, "product.partNumber", true));
		//complianceRecordDTO.setPcfId(validateAndConvertToUUID(getNodeText(node, "id", true)));
		complianceRecordDTO.setPcfId(getNodeText(node, "id", true));
		complianceRecordDTO.setPrecedingPcfId(getNodeText(node, "precedingPcfId", false));
		complianceRecordDTO.setSpecVersion(getAdditionalInfoValue(node, "specVersion"));
		String partialFullPcf = getAdditionalInfoValue(node, "partialfullpcf");
		if (!"Cradle-to-gate".equals(partialFullPcf) && !"Cradle-to-grave".equals(partialFullPcf)) {
			partialFullPcf = "Cradle-to-gate";
		}
		complianceRecordDTO.setPartialFullPcf(partialFullPcf);
		String versionStr = getAdditionalInfoValue(node, "version");
		int version;
		if (versionStr != null && !versionStr.isEmpty()) {
			try {
				version = (int) Float.parseFloat(versionStr);
				complianceRecordDTO.setVersion(version);
			} catch (NumberFormatException e) {
			}
		}
		complianceRecordDTO.setCreated(now.format(formatter));
		String validityStart = now.format(formatter);
		complianceRecordDTO.setValidityPeriodStart(validityStart);

		String validityEnd = now.plusMonths(12).format(formatter);
		complianceRecordDTO.setValidityPeriodEnd(validityEnd);

		complianceRecordDTO.setStatus(getNodeText(node, "status", true));
		complianceRecordDTO.setComment(getNodeText(node, "comment", false));
		complianceRecordDTO.setPcfLegalStatement(getNodeText(node, "pcfLegalStatement", false));
		complianceRecordDTO.setDeclaredUnit(getAdditionalInfoValue(node, "declaredUnit"));
		String unitaryProductAmountStr = getAdditionalInfoValue(node, "unitaryProductAmount");
		unitaryProductAmountStr = formatDecimalValue(unitaryProductAmountStr);
		complianceRecordDTO.setUnitaryProductAmount(unitaryProductAmountStr);
		String productMassPerDeclaredUnitStr = getNodeText(node, "product.measurementUnit", true);
		productMassPerDeclaredUnitStr = formatDecimalValue(productMassPerDeclaredUnitStr);
		complianceRecordDTO.setProductMassPerDeclaredUnit(productMassPerDeclaredUnitStr);
		complianceRecordDTO.setExemptedEmissionsPercent(getAdditionalInfoValue(node, "exemptedEmissionsPercent"));
		complianceRecordDTO
				.setExemptedEmissionsDescription(getAdditionalInfoValue(node, "exemptedEmissionsDescription"));
		complianceRecordDTO.setPackagingEmissionsIncluded(getAdditionalInfoValue(node, "packagingEmissionsIncluded"));
		complianceRecordDTO
				.setGeographyCountrySubdivision(getNodeText(node, "customAttributes.countrySubDivisionString", false));
		String geographyCountry = getNodeText(node, "location.country", false);
		complianceRecordDTO.setGeographyCountry(convertCountryCode(geographyCountry));
		complianceRecordDTO.setGeographyRegionOrSubregion(getNodeText(node, "location.geo", true));
		complianceRecordDTO.setBoundaryProcessesDescription(getAdditionalInfoValue(node, "boundaryProcessDescription"));

		complianceRecordDTO
				.setReferencePeriodStart(formatDateTime(getAdditionalInfoValue(node, "referencePeriodStart"), true));
		complianceRecordDTO
				.setReferencePeriodEnd(formatDateTime(getAdditionalInfoValue(node, "referencePeriodEnd"), false));
		complianceRecordDTO.setCharacterizationFactors(
				validateCharacterizationFactors(getAdditionalInfoValue(node, "characterizationFactors")));
		complianceRecordDTO.setAllocationRulesDescription(getAdditionalInfoValue(node, "allocationRulesDescription"));
		complianceRecordDTO.setAllocationWasteIncineration(
				validateAllocationWasteIncineration(getAdditionalInfoValue(node, "allocationWasteIncineration")));
		complianceRecordDTO.setPrimaryDataShare(getNodeText(node, "scorePrimaryDataRatio", false));
		complianceRecordDTO.setCoveragePercent(getNodeText(node, "coveragePercent", false));
		complianceRecordDTO.setTechnologicalDqr(getNodeText(node, "technologicalDqr", false));
		complianceRecordDTO.setTemporalDqr(getNodeText(node, "temporalDqr", false));
		complianceRecordDTO.setGeographicalDqr(getNodeText(node, "geographicalDqr", false));
		complianceRecordDTO.setCompletenessDqr(getNodeText(node, "completenessDqr", false));
		complianceRecordDTO.setReliabilityDqr(getNodeText(node, "reliabilityDqr", false));
		complianceRecordDTO.setPcfExcludingBiogenic(getAdditionalInfoValue(node, "pcfExcludingBiogenic"));
		complianceRecordDTO.setPcfIncludingBiogenic(getAdditionalInfoValue(node, "pcfIncludingBiogenic"));
		complianceRecordDTO.setFossilGhgEmissions(getAdditionalInfoValue(node, "fossilGhgEmissions"));
		complianceRecordDTO.setBiogenicCarbonEmissionsOtherThanCo2(
				getAdditionalInfoValue(node, "biogenicCarbonEmissionsOotherThanCo2"));
		complianceRecordDTO.setBiogenicCarbonWithdrawal(getAdditionalInfoValue(node, "biogenicCarbonWithdrawal"));
		complianceRecordDTO.setDlucGhgEmissions(getAdditionalInfoValue(node, "dlucGhgEmissions"));
		complianceRecordDTO.setLuGhgEmissions(getAdditionalInfoValue(node, "luGhgEmissions"));
		complianceRecordDTO.setAircraftGhgEmissions(getAdditionalInfoValue(node, "aircraftGhgEmissions"));
		complianceRecordDTO.setPackagingGhgEmissions(getAdditionalInfoValue(node, "packagingGhgEmissions"));
		complianceRecordDTO
				.setDistributionPcfExcludingBiogenic(getAdditionalInfoValue(node, "distributionPcfExcludingIogenic"));
		complianceRecordDTO
				.setDistributionPcfIncludingBiogenic(getAdditionalInfoValue(node, "distributionPcfIncludingBiogenic"));
		complianceRecordDTO
				.setDistributionFossilGhgEmissions(getAdditionalInfoValue(node, "distributionFossilGhgEmissions"));
		complianceRecordDTO.setDistributionBiogenicCarbonEmissionsOtherThanCo2(
				getAdditionalInfoValue(node, "distributionBiogenicCarbonEmissionsOtherThanCo2"));
		complianceRecordDTO.setDistributionBiogenicCarbonWithdrawal(
				getAdditionalInfoValue(node, "distributionBiogenicCarbonWithdrawal"));
		complianceRecordDTO
				.setDistributionDlucGhgEmissions(getAdditionalInfoValue(node, "distributionDlucGhgEmissions"));
		complianceRecordDTO.setDistributionLuGhgEmissions(getAdditionalInfoValue(node, "distributionLuGhgEmissions"));
		complianceRecordDTO
				.setDistributionAircraftGhgEmissions(getAdditionalInfoValue(node, "distributionAircraftGhgEmissions"));

		String fossilCarbonContent = getAdditionalInfoValue(node, "fossilCarbonContent");
		String biogenicCarbonContent = getAdditionalInfoValue(node, "biogenicCarbonContent");

		complianceRecordDTO.setFossilCarbonContent(fossilCarbonContent);
		complianceRecordDTO.setBiogenicCarbonContent(biogenicCarbonContent);

		if (fossilCarbonContent == null || fossilCarbonContent.isEmpty() || biogenicCarbonContent == null
				|| biogenicCarbonContent.isEmpty()) {
			complianceRecordDTO.setCarbonContentTotal("");
		} else {
			try {
				double fossilCarbon = Double.parseDouble(fossilCarbonContent);
				double biogenicCarbon = Double.parseDouble(biogenicCarbonContent);
				double totalCarbonContent = fossilCarbon + biogenicCarbon;
				complianceRecordDTO.setCarbonContentTotal(String.valueOf(totalCarbonContent));
			} catch (NumberFormatException e) {
				complianceRecordDTO.setCarbonContentTotal("");
			}
		}
		complianceRecordDTO.setCrossSectoralStandard(
				validateCrossSectoralStandard(getAdditionalInfoValue(node, "crossSectoralStandardsUsed")));
		complianceRecordDTO.setStandardPcfId(getNodeText(node, "id", true));

		return complianceRecordDTO;
	}

	private void validateDTO(ComplianceRecordDTO complianceRecordDTO) throws ProcessingException {
		Set<ConstraintViolation<ComplianceRecordDTO>> violations = validator.validate(complianceRecordDTO);
		if (!violations.isEmpty()) {
			StringBuilder errorMessage = new StringBuilder("Validation errors: ");
			for (ConstraintViolation<ComplianceRecordDTO> violation : violations) {
				errorMessage.append(violation.getMessage()).append("; ");
			}
			throw new ProcessingException(errorMessage.toString());
		}
	}

	private void convertToPcfCsv(JsonNode jsonTree) throws IOException, ProcessingException {

		LocalDateTime now = LocalDateTime.now();
		String timestamp = serviceUtil.formatDateTime(now);

		String fileName = "PCF.csv";

		File tempCsvFile = serviceUtil.createTempFile("PCF", ".csv");

		try (FileWriter writer = new FileWriter(tempCsvFile);
				CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader("material_number", "pcf_id",
						"preceding_pcf_id", "specversion", "partialfullpcf", "version", "created", "status",
						"validityperiodstart", "validityperiodend", "comment", "pcflegalstatement", "declared_unit",
						"unitary_product_amount", "product_mass_per_declared_unit", "exempted_emissions_percent",
						"exempted_emissions_description", "packaging_emissions_included",
						"geography_country_subdivision", "geography_country", "geography_region_or_subregion",
						"boundary_processes_description", "reference_period_start", "reference_period_end",
						"characterization_factors", "allocation_rules_description", "allocation_waste_incineration",
						"primary_data_share", "coverage_percent", "technological_dqr", "temporal_dqr",
						"geographical_dqr", "completeness_dqr", "reliability_dqr", "pcf_excluding_biogenic",
						"pcf_including_biogenic", "fossil_ghg_emissions", "biogenic_carbon_emissions_other_than_co2",
						"biogenic_carbon_withdrawal", "dluc_ghg_emissions", "lu_ghg_emissions",
						"aircraft_ghg_emissions", "packaging_ghg_emissions", "distribution_pcf_excluding_biogenic",
						"distribution_pcf_including_biogenic", "distribution_fossil_ghg_emissions",
						"distribution_biogenic_carbon_emissions_other_than_co2",
						"distribution_biogenic_carbon_withdrawal", "distribution_dluc_ghg_emissions",
						"distribution_lu_ghg_emissions", "distribution_aircraftghg_emissions", "carbon_content_total",
						"fossil_carbon_content", "biogenic_carbon_content"))) {
			for (JsonNode node : jsonTree) {
				ComplianceRecordDTO complianceRecordDTO = createComplianceRecordDTO(node);
				csvPrinter.printRecord(convertNullToString(complianceRecordDTO.getMaterialNumber()),
						convertNullToString(complianceRecordDTO.getPcfId()),
						convertNullToString(complianceRecordDTO.getPrecedingPcfId()),
						convertNullToString(complianceRecordDTO.getSpecVersion()),
						convertNullToString(complianceRecordDTO.getPartialFullPcf()),
						convertNullToString(complianceRecordDTO.getVersion()),
						convertNullToString(complianceRecordDTO.getCreated()),
						convertNullToString(complianceRecordDTO.getStatus()),
						convertNullToString(complianceRecordDTO.getValidityPeriodStart()),
						convertNullToString(complianceRecordDTO.getValidityPeriodEnd()),
						convertNullToString(complianceRecordDTO.getComment()),
						convertNullToString(complianceRecordDTO.getPcfLegalStatement()),
						convertNullToString(complianceRecordDTO.getDeclaredUnit()),
						convertNullToString(complianceRecordDTO.getUnitaryProductAmount()),
						convertNullToString(complianceRecordDTO.getProductMassPerDeclaredUnit()),
						convertNullToString(complianceRecordDTO.getExemptedEmissionsPercent()),
						convertNullToString(complianceRecordDTO.getExemptedEmissionsDescription()),
						convertNullToString(complianceRecordDTO.getPackagingEmissionsIncluded()),
						convertNullToString(complianceRecordDTO.getGeographyCountrySubdivision()),
						convertNullToString(complianceRecordDTO.getGeographyCountry()),
						convertNullToString(complianceRecordDTO.getGeographyRegionOrSubregion()),
						convertNullToString(complianceRecordDTO.getBoundaryProcessesDescription()),
						convertNullToString(complianceRecordDTO.getReferencePeriodStart()),
						convertNullToString(complianceRecordDTO.getReferencePeriodEnd()),
						convertNullToString(complianceRecordDTO.getCharacterizationFactors()),
						convertNullToString(complianceRecordDTO.getAllocationRulesDescription()),
						convertNullToString(complianceRecordDTO.getAllocationWasteIncineration()),
						convertNullToString(complianceRecordDTO.getPrimaryDataShare()),
						convertNullToString(complianceRecordDTO.getCoveragePercent()),
						convertNullToString(complianceRecordDTO.getTechnologicalDqr()),
						convertNullToString(complianceRecordDTO.getTemporalDqr()),
						convertNullToString(complianceRecordDTO.getGeographicalDqr()),
						convertNullToString(complianceRecordDTO.getCompletenessDqr()),
						convertNullToString(complianceRecordDTO.getReliabilityDqr()),
						convertNullToString(complianceRecordDTO.getPcfExcludingBiogenic()),
						convertNullToString(complianceRecordDTO.getPcfIncludingBiogenic()),
						convertNullToString(complianceRecordDTO.getFossilGhgEmissions()),
						convertNullToString(complianceRecordDTO.getBiogenicCarbonEmissionsOtherThanCo2()),
						convertNullToString(complianceRecordDTO.getBiogenicCarbonWithdrawal()),
						convertNullToString(complianceRecordDTO.getDlucGhgEmissions()),
						convertNullToString(complianceRecordDTO.getLuGhgEmissions()),
						convertNullToString(complianceRecordDTO.getAircraftGhgEmissions()),
						convertNullToString(complianceRecordDTO.getPackagingGhgEmissions()),
						convertNullToString(complianceRecordDTO.getDistributionPcfExcludingBiogenic()),
						convertNullToString(complianceRecordDTO.getDistributionPcfIncludingBiogenic()),
						convertNullToString(complianceRecordDTO.getDistributionFossilGhgEmissions()),
						convertNullToString(complianceRecordDTO.getDistributionBiogenicCarbonEmissionsOtherThanCo2()),
						convertNullToString(complianceRecordDTO.getDistributionBiogenicCarbonWithdrawal()),
						convertNullToString(complianceRecordDTO.getDistributionDlucGhgEmissions()),
						convertNullToString(complianceRecordDTO.getDistributionLuGhgEmissions()),
						convertNullToString(complianceRecordDTO.getDistributionAircraftGhgEmissions()),
						convertNullToString(complianceRecordDTO.getCarbonContentTotal()),
						convertNullToString(complianceRecordDTO.getFossilCarbonContent()),
						convertNullToString(complianceRecordDTO.getBiogenicCarbonContent()));
			}
		}

		String cosKey = "upload/pcf/" + fileName;
		serviceUtil.uploadFileToCos(cosClient, bucketName, cosKey, tempCsvFile);
		Path resourcesDir = Paths.get("src", "main", "resources", "import", "partners");
		if (!Files.exists(resourcesDir)) {
			Files.createDirectories(resourcesDir);
		}
		Path destinationFile = resourcesDir.resolve(fileName);
		Files.copy(tempCsvFile.toPath(), destinationFile, StandardCopyOption.REPLACE_EXISTING);
		tempCsvFile.delete();

	}

	private Object convertNullToString(UUID value) {
		return (value == null || "null".equals(value)) ? "" : value;
	}

	private Object convertNullToString(Integer value) {
		return (value == null || "null".equals(value)) ? "" : value;
	}

	private void convertToPcfStandardCsv(JsonNode jsonTree) throws IOException, ProcessingException {

		LocalDateTime now = LocalDateTime.now();
		String timestamp = serviceUtil.formatDateTime(now);

		String fileName = "PCF Standard.csv";

		File tempCsvFile = serviceUtil.createTempFile("PCF Standard", ".csv");

		try (FileWriter writer = new FileWriter(tempCsvFile);
				CSVPrinter csvPrinter = new CSVPrinter(writer,
						CSVFormat.DEFAULT.withHeader("pcf_id", "cross_sectoral_standard"))) {
			for (JsonNode node : jsonTree) {
				ComplianceRecordDTO complianceRecordDTO = createComplianceRecordDTO(node);
				csvPrinter.printRecord(convertNullToString(complianceRecordDTO.getStandardPcfId()),
						convertNullToString(complianceRecordDTO.getCrossSectoralStandard()));
			}
		}

		String cosKey = "upload/pcf-standard/" + fileName;
		serviceUtil.uploadFileToCos(cosClient, bucketName, cosKey, tempCsvFile);
		Path resourcesDir = Paths.get("src", "main", "resources", "import", "partners");
		if (!Files.exists(resourcesDir)) {
			Files.createDirectories(resourcesDir);
		}
		Path destinationFile = resourcesDir.resolve(fileName);
		Files.copy(tempCsvFile.toPath(), destinationFile, StandardCopyOption.REPLACE_EXISTING);
		tempCsvFile.delete();

	}

	private String getNodeText(JsonNode node, String fieldName, boolean returnNullIfMissing) {
		JsonNode fieldNode = node.at("/" + fieldName.replace(".", "/"));
		if (fieldNode != null && !fieldNode.isMissingNode()) {
			return fieldNode.asText();
		} else {
			return returnNullIfMissing ? null : "";
		}
	}

	private String getAdditionalInfoValue(JsonNode node, String name) {
		if (node.has("additionalInfo")) {
			for (JsonNode infoNode : node.get("additionalInfo")) {
				if (infoNode.has("name") && infoNode.get("name").asText().equals(name)) {
					String value = infoNode.get("value").asText();
					return (value == null || "null".equals(value)) ? "" : value;
				}
			}
		}
		return "";
	}

	private String convertNullToString(String value) {
		return (value == null || "null".equals(value)) ? "" : value;
	}

	private UUID validateAndConvertToUUID(String uuidStr) {
		if (uuidStr != null && !uuidStr.isEmpty()) {
			try {
				return UUID.fromString(uuidStr);
			} catch (IllegalArgumentException e) {

				return null;
			}
		}
		return null;
	}

	private String formatDecimalValue(String value) {
		if (value == null || value.isEmpty()) {
			return value;
		}
		try {
			DecimalFormat decimalFormat = new DecimalFormat("#.0");
			decimalFormat.setMinimumFractionDigits(1);
			decimalFormat.setMaximumFractionDigits(1);
			return decimalFormat.format(Double.parseDouble(value));
		} catch (NumberFormatException e) {

			return value;
		}
	}

	private String ensureFullDateTime(String dateTime, boolean isStart) {
		if (dateTime != null && !dateTime.isEmpty()) {

			if (!dateTime.contains("T")) {

				dateTime += isStart ? START_TIME_COMPONENT : END_TIME_COMPONENT;
			}
		}
		return dateTime;
	}

	private String validateCharacterizationFactors(String value) {
		if ("AR5".equals(value) || "AR6".equals(value)) {
			return value;
		} else {
			return "AR6";
		}
	}

	private String validateAllocationWasteIncineration(String value) {
		if ("cut-off".equals(value) || "reverse cut-off".equals(value) || "system expansion".equals(value)) {
			return value;
		} else {
			return "cut-off";
		}
	}

	private String validateCrossSectoralStandard(String value) {
		if ("GHG Protocol Product standard".equals(value) || "ISO Standard 14067".equals(value)
				|| "ISO Standard 14044".equals(value)) {
			return value;
		} else {
			return "GHG Protocol Product standard";
		}
	}

	private String convertCountryCode(String code) {
		if (code.length() == 2) {

			return code;
		} else if (code.length() == 3) {

			for (Locale locale : Locale.getAvailableLocales()) {
				if (locale.getISO3Country().equals(code)) {
					return locale.getCountry();
				}
			}
		}
		return "Invalid country code";
	}

	private String formatDateTime(String date, boolean isStart) {
		if (date == null || date.isEmpty()) {
			return date;
		}
		try {
			LocalDate localDate = LocalDate.parse(date);
			LocalDateTime localDateTime;
			if (isStart) {
				localDateTime = localDate.atStartOfDay();
			} else {
				localDateTime = localDate.atTime(23, 59, 59, 999999999);
			}
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
			return localDateTime.format(formatter);
		} catch (Exception e) {

			return date;
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