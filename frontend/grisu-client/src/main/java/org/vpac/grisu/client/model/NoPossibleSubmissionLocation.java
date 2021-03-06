package org.vpac.grisu.client.model;

public class NoPossibleSubmissionLocation extends Exception {

	private String version = null;
	private String application = null;
	public String getVersion() {
		return version;
	}

	public String getApplication() {
		return application;
	}

	public String getFqan() {
		return fqan;
	}

	private String fqan = null;
	
	public NoPossibleSubmissionLocation(String application, String version, String fqan) {
		super("No possible submissionlocation for application "+application+", version "+version+" and fqan "+fqan+".");
		this.application = application;
		this.version = version;
		this.fqan = fqan;
	}
	
}
