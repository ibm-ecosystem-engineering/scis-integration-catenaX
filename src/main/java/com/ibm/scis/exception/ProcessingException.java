package com.ibm.scis.exception;

@SuppressWarnings("serial")
public class ProcessingException extends Exception {
	public ProcessingException(String message) {
		super(message);
		System.out.println(message);
	}

	
}

