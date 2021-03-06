

package org.vpac.grisu.client.model.login;

import org.vpac.grisu.control.ServiceInterface;


/**
 * Holds the login panels.
 * 
 * @author Markus Binsteiner
 *
 */
public interface LoginPanelsHolder {
	
	public LoginParams getLoginParams ();
	
	public ServiceInterface getServiceInterface();
	
	//public void setServiceInterface(ServiceInterface serviceInterface);
	
	public void loggedIn(ServiceInterface serviceInterface);
	
	public void cancelled();

}
