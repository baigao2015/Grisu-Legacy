package org.vpac.grisu.client.model.template.modules;

import org.vpac.grisu.client.control.eventStuff.SubmissionObjectListener;
import org.vpac.grisu.client.model.SubmissionObject;
import org.vpac.grisu.control.JobCreationException;

public interface SubmissionObjectHolder {
	
	public void addSubmissionObjectListener(SubmissionObjectListener l);
	
	public void removeSubmissionObjectListener(SubmissionObjectListener l);
	
	public SubmissionObject getCurrentSubmissionObject() throws JobCreationException;
	
}
