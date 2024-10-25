package com.ibm.scis.model;

import java.util.UUID;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class ComplianceRecordDTO {
	@NotNull(message = "Material number is mandatory")
	@NotEmpty(message = "Material number cannot be empty")
	private String materialNumber;

	@NotNull(message = "PCF ID is mandatory")
	private String pcfId;

	private String precedingPcfId;

	@NotNull(message = "Spec Version is mandatory")
	@NotEmpty(message = "Spec Version cannot be empty")
	private String specVersion;

	@NotNull(message = "Partial Full PCF is mandatory")
	@NotEmpty(message = "Partial Full PCF cannot be empty")
	private String partialFullPcf;

	@NotNull(message = "version is mandatory")
	private Integer version;

	@NotNull(message = "Created is mandatory")
	@NotEmpty(message = "Created cannot be empty")
	private String created;

	@NotNull(message = "Partial Full PCF is mandatory")
	@NotEmpty(message = "Partial Full PCF cannot be empty")
	private String status;

	private String validityPeriodStart;

	private String validityPeriodEnd;

	private String comment;

	private String pcfLegalStatement;

	@NotNull(message = "Declared Unit is mandatory")
	@NotEmpty(message = "Declared Unit cannot be empty")
	private String declaredUnit;

	@NotNull(message = "Unitary Product Amount is mandatory")
	@NotEmpty(message = "Unitary Product Amount cannot be empty")
	private String unitaryProductAmount;

	@NotNull(message = "Product Mass Per Declared Unit is mandatory")
	@NotEmpty(message = "Product Mass Per Declared Unit cannot be empty")
	private String productMassPerDeclaredUnit;

	@NotNull(message = "Exempted Emissions Percent is mandatory")
	@NotEmpty(message = "Exempted Emissions Percent cannot be empty")
	private String exemptedEmissionsPercent;

	private String exemptedEmissionsDescription;

	@NotNull(message = "Packaging Emissions Included is mandatory")
	@NotEmpty(message = "Packaging Emissions Included cannot be empty")
	private String packagingEmissionsIncluded;

	private String geographyCountrySubdivision;

	@Size(min = 2, max = 2, message = "Geography country must be exactly 2 characters")
	private String geographyCountry;

	@NotNull(message = "Geography Region or Sub Region is mandatory")
	@NotEmpty(message = "Geography Region or Sub Region cannot be empty")
	private String geographyRegionOrSubregion;

	private String boundaryProcessesDescription;

	@NotNull(message = "Reference Period Start is mandatory")
	@NotEmpty(message = "Reference Period Start cannot be empty")
	private String referencePeriodStart;

	@NotNull(message = "Reference Period End is mandatory")
	@NotEmpty(message = "Reference Period End cannot be empty")
	private String referencePeriodEnd;

	@NotNull(message = "Characterization Factors is mandatory")
	@NotEmpty(message = "Characterization Factors cannot be empty")
	private String characterizationFactors;

	private String allocationRulesDescription;

	@NotNull(message = "Allocation Waste Incineration is mandatory")
	@NotEmpty(message = "Allocation Waste Incineration cannot be empty")
	private String allocationWasteIncineration;

	private String primaryDataShare;

	private String coveragePercent;

	private String technologicalDqr;

	private String temporalDqr;

	private String geographicalDqr;

	private String completenessDqr;

	private String reliabilityDqr;

	@NotNull(message = "PCF Excluding Biogenic is mandatory")
	@NotEmpty(message = "PCF Excluding Biogenic cannot be empty")
	private String pcfExcludingBiogenic;

	private String pcfIncludingBiogenic;

	private String fossilGhgEmissions;

	private String biogenicCarbonEmissionsOtherThanCo2;
	private String biogenicCarbonWithdrawal;
	private String dlucGhgEmissions;
	private String luGhgEmissions;
	private String aircraftGhgEmissions;
	private String packagingGhgEmissions;
	private String distributionPcfExcludingBiogenic;
	private String distributionPcfIncludingBiogenic;
	private String distributionFossilGhgEmissions;
	private String distributionBiogenicCarbonEmissionsOtherThanCo2;
	private String distributionBiogenicCarbonWithdrawal;
	private String distributionDlucGhgEmissions;
	private String distributionLuGhgEmissions;
	private String distributionAircraftGhgEmissions;
	private String carbonContentTotal;
	private String fossilCarbonContent;
	private String biogenicCarbonContent;

	@NotNull(message = "Standard PCF Id is mandatory")
	@NotEmpty(message = "Standard PCF Id cannot be empty")
	private String standardPcfId;

	@NotNull(message = "Cross Sectoral Standard is mandatory")
	@NotEmpty(message = "Cross Sectoral Standard cannot be empty")
	private String crossSectoralStandard;

	public String getMaterialNumber() {
		return materialNumber;
	}

	public void setMaterialNumber(String materialNumber) {
		this.materialNumber = materialNumber;
	}

	public String getPcfId() {
		return pcfId;
	}

	public void setPcfId(String pcfId) {
		this.pcfId = pcfId;
	}

	public String getPrecedingPcfId() {
		return precedingPcfId;
	}

	public void setPrecedingPcfId(String precedingPcfId) {
		this.precedingPcfId = precedingPcfId;
	}

	public String getSpecVersion() {
		return specVersion;
	}

	public void setSpecVersion(String specVersion) {
		this.specVersion = specVersion;
	}

	public String getPartialFullPcf() {
		return partialFullPcf;
	}

	public void setPartialFullPcf(String partialFullPcf) {
		this.partialFullPcf = partialFullPcf;
	}

	public Integer getVersion() {
		return version;
	}

	public void setVersion(Integer version) {
		this.version = version;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getValidityPeriodEnd() {
		return validityPeriodEnd;
	}

	public void setValidityPeriodEnd(String validityPeriodEnd) {
		this.validityPeriodEnd = validityPeriodEnd;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public String getPcfLegalStatement() {
		return pcfLegalStatement;
	}

	public void setPcfLegalStatement(String pcfLegalStatement) {
		this.pcfLegalStatement = pcfLegalStatement;
	}

	public String getDeclaredUnit() {
		return declaredUnit;
	}

	public void setDeclaredUnit(String declaredUnit) {
		this.declaredUnit = declaredUnit;
	}

	public String getUnitaryProductAmount() {
		return unitaryProductAmount;
	}

	public void setUnitaryProductAmount(String unitaryProductAmount) {
		this.unitaryProductAmount = unitaryProductAmount;
	}

	public String getProductMassPerDeclaredUnit() {
		return productMassPerDeclaredUnit;
	}

	public void setProductMassPerDeclaredUnit(String productMassPerDeclaredUnit) {
		this.productMassPerDeclaredUnit = productMassPerDeclaredUnit;
	}

	public String getExemptedEmissionsPercent() {
		return exemptedEmissionsPercent;
	}

	public void setExemptedEmissionsPercent(String exemptedEmissionsPercent) {
		this.exemptedEmissionsPercent = exemptedEmissionsPercent;
	}

	public String getExemptedEmissionsDescription() {
		return exemptedEmissionsDescription;
	}

	public void setExemptedEmissionsDescription(String exemptedEmissionsDescription) {
		this.exemptedEmissionsDescription = exemptedEmissionsDescription;
	}

	public String getPackagingEmissionsIncluded() {
		return packagingEmissionsIncluded;
	}

	public void setPackagingEmissionsIncluded(String packagingEmissionsIncluded) {
		this.packagingEmissionsIncluded = packagingEmissionsIncluded;
	}

	public String getGeographyCountrySubdivision() {
		return geographyCountrySubdivision;
	}

	public void setGeographyCountrySubdivision(String geographyCountrySubdivision) {
		this.geographyCountrySubdivision = geographyCountrySubdivision;
	}

	public String getGeographyCountry() {
		return geographyCountry;
	}

	public void setGeographyCountry(String geographyCountry) {
		this.geographyCountry = geographyCountry;
	}

	public String getGeographyRegionOrSubregion() {
		return geographyRegionOrSubregion;
	}

	public void setGeographyRegionOrSubregion(String geographyRegionOrSubregion) {
		this.geographyRegionOrSubregion = geographyRegionOrSubregion;
	}

	public String getBoundaryProcessesDescription() {
		return boundaryProcessesDescription;
	}

	public void setBoundaryProcessesDescription(String boundaryProcessesDescription) {
		this.boundaryProcessesDescription = boundaryProcessesDescription;
	}

	public String getReferencePeriodStart() {
		return referencePeriodStart;
	}

	public void setReferencePeriodStart(String referencePeriodStart) {
		this.referencePeriodStart = referencePeriodStart;
	}

	public String getReferencePeriodEnd() {
		return referencePeriodEnd;
	}

	public void setReferencePeriodEnd(String referencePeriodEnd) {
		this.referencePeriodEnd = referencePeriodEnd;
	}

	public String getCharacterizationFactors() {
		return characterizationFactors;
	}

	public void setCharacterizationFactors(String characterizationFactors) {
		this.characterizationFactors = characterizationFactors;
	}

	public String getAllocationRulesDescription() {
		return allocationRulesDescription;
	}

	public void setAllocationRulesDescription(String allocationRulesDescription) {
		this.allocationRulesDescription = allocationRulesDescription;
	}

	public String getAllocationWasteIncineration() {
		return allocationWasteIncineration;
	}

	public void setAllocationWasteIncineration(String allocationWasteIncineration) {
		this.allocationWasteIncineration = allocationWasteIncineration;
	}

	public String getPrimaryDataShare() {
		return primaryDataShare;
	}

	public void setPrimaryDataShare(String primaryDataShare) {
		this.primaryDataShare = primaryDataShare;
	}

	public String getCoveragePercent() {
		return coveragePercent;
	}

	public void setCoveragePercent(String coveragePercent) {
		this.coveragePercent = coveragePercent;
	}

	public String getTechnologicalDqr() {
		return technologicalDqr;
	}

	public void setTechnologicalDqr(String technologicalDqr) {
		this.technologicalDqr = technologicalDqr;
	}

	public String getTemporalDqr() {
		return temporalDqr;
	}

	public void setTemporalDqr(String temporalDqr) {
		this.temporalDqr = temporalDqr;
	}

	public String getGeographicalDqr() {
		return geographicalDqr;
	}

	public void setGeographicalDqr(String geographicalDqr) {
		this.geographicalDqr = geographicalDqr;
	}

	public String getCompletenessDqr() {
		return completenessDqr;
	}

	public void setCompletenessDqr(String completenessDqr) {
		this.completenessDqr = completenessDqr;
	}

	public String getReliabilityDqr() {
		return reliabilityDqr;
	}

	public void setReliabilityDqr(String reliabilityDqr) {
		this.reliabilityDqr = reliabilityDqr;
	}

	public String getPcfExcludingBiogenic() {
		return pcfExcludingBiogenic;
	}

	public void setPcfExcludingBiogenic(String pcfExcludingBiogenic) {
		this.pcfExcludingBiogenic = pcfExcludingBiogenic;
	}

	public String getPcfIncludingBiogenic() {
		return pcfIncludingBiogenic;
	}

	public void setPcfIncludingBiogenic(String pcfIncludingBiogenic) {
		this.pcfIncludingBiogenic = pcfIncludingBiogenic;
	}

	public String getFossilGhgEmissions() {
		return fossilGhgEmissions;
	}

	public void setFossilGhgEmissions(String fossilGhgEmissions) {
		this.fossilGhgEmissions = fossilGhgEmissions;
	}

	public String getBiogenicCarbonEmissionsOtherThanCo2() {
		return biogenicCarbonEmissionsOtherThanCo2;
	}

	public void setBiogenicCarbonEmissionsOtherThanCo2(String biogenicCarbonEmissionsOtherThanCo2) {
		this.biogenicCarbonEmissionsOtherThanCo2 = biogenicCarbonEmissionsOtherThanCo2;
	}

	public String getBiogenicCarbonWithdrawal() {
		return biogenicCarbonWithdrawal;
	}

	public void setBiogenicCarbonWithdrawal(String biogenicCarbonWithdrawal) {
		this.biogenicCarbonWithdrawal = biogenicCarbonWithdrawal;
	}

	public String getDlucGhgEmissions() {
		return dlucGhgEmissions;
	}

	public void setDlucGhgEmissions(String dlucGhgEmissions) {
		this.dlucGhgEmissions = dlucGhgEmissions;
	}

	public String getLuGhgEmissions() {
		return luGhgEmissions;
	}

	public void setLuGhgEmissions(String luGhgEmissions) {
		this.luGhgEmissions = luGhgEmissions;
	}

	public String getAircraftGhgEmissions() {
		return aircraftGhgEmissions;
	}

	public void setAircraftGhgEmissions(String aircraftGhgEmissions) {
		this.aircraftGhgEmissions = aircraftGhgEmissions;
	}

	public String getPackagingGhgEmissions() {
		return packagingGhgEmissions;
	}

	public void setPackagingGhgEmissions(String packagingGhgEmissions) {
		this.packagingGhgEmissions = packagingGhgEmissions;
	}

	public String getDistributionPcfIncludingBiogenic() {
		return distributionPcfIncludingBiogenic;
	}

	public void setDistributionPcfIncludingBiogenic(String distributionPcfIncludingBiogenic) {
		this.distributionPcfIncludingBiogenic = distributionPcfIncludingBiogenic;
	}

	public String getDistributionFossilGhgEmissions() {
		return distributionFossilGhgEmissions;
	}

	public void setDistributionFossilGhgEmissions(String distributionFossilGhgEmissions) {
		this.distributionFossilGhgEmissions = distributionFossilGhgEmissions;
	}

	public String getDistributionBiogenicCarbonEmissionsOtherThanCo2() {
		return distributionBiogenicCarbonEmissionsOtherThanCo2;
	}

	public void setDistributionBiogenicCarbonEmissionsOtherThanCo2(
			String distributionBiogenicCarbonEmissionsOtherThanCo2) {
		this.distributionBiogenicCarbonEmissionsOtherThanCo2 = distributionBiogenicCarbonEmissionsOtherThanCo2;
	}

	public String getDistributionBiogenicCarbonWithdrawal() {
		return distributionBiogenicCarbonWithdrawal;
	}

	public void setDistributionBiogenicCarbonWithdrawal(String distributionBiogenicCarbonWithdrawal) {
		this.distributionBiogenicCarbonWithdrawal = distributionBiogenicCarbonWithdrawal;
	}

	public String getDistributionDlucGhgEmissions() {
		return distributionDlucGhgEmissions;
	}

	public void setDistributionDlucGhgEmissions(String distributionDlucGhgEmissions) {
		this.distributionDlucGhgEmissions = distributionDlucGhgEmissions;
	}

	public String getDistributionLuGhgEmissions() {
		return distributionLuGhgEmissions;
	}

	public void setDistributionLuGhgEmissions(String distributionLuGhgEmissions) {
		this.distributionLuGhgEmissions = distributionLuGhgEmissions;
	}

	public String getDistributionAircraftGhgEmissions() {
		return distributionAircraftGhgEmissions;
	}

	public void setDistributionAircraftGhgEmissions(String distributionAircraftGhgEmissions) {
		this.distributionAircraftGhgEmissions = distributionAircraftGhgEmissions;
	}

	public String getCarbonContentTotal() {
		return carbonContentTotal;
	}

	public void setCarbonContentTotal(String carbonContentTotal) {
		this.carbonContentTotal = carbonContentTotal;
	}

	public String getFossilCarbonContent() {
		return fossilCarbonContent;
	}

	public void setFossilCarbonContent(String fossilCarbonContent) {
		this.fossilCarbonContent = fossilCarbonContent;
	}

	public String getBiogenicCarbonContent() {
		return biogenicCarbonContent;
	}

	public void setBiogenicCarbonContent(String biogenicCarbonContent) {
		this.biogenicCarbonContent = biogenicCarbonContent;
	}

	public String getCreated() {
		return created;
	}

	public void setCreated(String created) {
		this.created = created;
	}

	public String getValidityPeriodStart() {
		return validityPeriodStart;
	}

	public void setValidityPeriodStart(String validityPeriodStart) {
		this.validityPeriodStart = validityPeriodStart;
	}

	public String getDistributionPcfExcludingBiogenic() {
		return distributionPcfExcludingBiogenic;
	}

	public void setDistributionPcfExcludingBiogenic(String distributionPcfExcludingBiogenic) {
		this.distributionPcfExcludingBiogenic = distributionPcfExcludingBiogenic;
	}

	public String getCrossSectoralStandard() {
		return crossSectoralStandard;
	}

	public void setCrossSectoralStandard(String crossSectoralStandard) {
		this.crossSectoralStandard = crossSectoralStandard;
	}

	public String getStandardPcfId() {
		return standardPcfId;
	}

	public void setStandardPcfId(String standardPcfId) {
		this.standardPcfId = standardPcfId;
	}

}
