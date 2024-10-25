package com.ibm.scis.model;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

public class OrganizationRecordDTO {

	@NotNull(message = "BPN is mandatory")
	@NotEmpty(message = "BPN cannot be empty")
	private String bpn;

	private String identifier;

	// Getters and Setters
	public String getBpn() {
		return bpn;
	}

	public void setBpn(String bpn) {
		this.bpn = bpn;
	}

	public String getIdentifier() {
		return identifier;
	}

	public void setIdentifier(String identifier) {
		this.identifier = identifier;
	}

	@Override
	public String toString() {
		return "OrganizationRecordDTO [bpn=" + bpn + ", identifier=" + identifier + "]";
	}

}