package com.ibm.scis.model;

import javax.validation.constraints.NotBlank;

public class ProductSupplierDTO {

	@NotBlank(message = "material number is mandatory")
	private String materialNumber;
	@NotBlank(message = "descriprion is mandatory")
	private String description;
	@NotBlank(message = "bpn is mandatory")
	private String bpn;
	@NotBlank(message = "type is mandatory")
	private String type;

	public String getMaterialNumber() {
		return materialNumber;
	}

	public void setMaterialNumber(String materialNumber) {
		this.materialNumber = materialNumber;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getBpn() {
		return bpn;
	}

	public void setBpn(String bpn) {
		this.bpn = bpn;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	@Override
	public String toString() {
		return "ProductSupplierDTO [materialNumber=" + materialNumber + ", description=" + description + ", bpn=" + bpn
				+ ", type=" + type + "]";
	}

}
