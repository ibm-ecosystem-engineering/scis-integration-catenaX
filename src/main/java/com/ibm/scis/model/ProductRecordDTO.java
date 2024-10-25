package com.ibm.scis.model;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class ProductRecordDTO {

	@NotNull(message = "Part number is mandatory")
	@NotEmpty(message = "Part number cannot be empty")
	private String partNumber;

	@NotNull(message = "Description is mandatory")
	@NotEmpty(message = "Description cannot be empty")
	private String description;

	private String validFrom;
	private String validTo;

	@NotNull(message = "createDate is mandatory")
	@NotEmpty(message = "createDate cannot be empty")
	private String createDate;

	@NotNull(message = "productCategory is mandatory")
	@NotEmpty(message = "productCategory cannot be empty")
	private String productCategory;

	// Getters and Setters
	public String getPartNumber() {
		return partNumber;
	}

	public void setPartNumber(String partNumber) {
		this.partNumber = partNumber;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getValidFrom() {
		return validFrom;
	}

	public void setValidFrom(String validFrom) {
		this.validFrom = validFrom;
	}

	public String getValidTo() {
		return validTo;
	}

	public void setValidTo(String validTo) {
		this.validTo = validTo;
	}

	public String getCreateDate() {
		return createDate;
	}

	public void setCreateDate(String createDate) {
		this.createDate = createDate;
	}

	public String getProductCategory() {
		return productCategory;
	}

	public void setProductCategory(String productCategory) {
		this.productCategory = productCategory;
	}

}