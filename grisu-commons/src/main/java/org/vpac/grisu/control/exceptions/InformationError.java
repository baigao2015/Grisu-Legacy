package org.vpac.grisu.control.exceptions;

public class InformationError extends Exception {
	
	public InformationError(String message, Exception e) {
		super(message,e);
	}
	
	public InformationError(String message) {
		super(message);
	}

}
