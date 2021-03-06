package org.vpac.grisu.client.gricli;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import javax.activation.DataSource;
import javax.activation.FileDataSource;

import jline.ConsoleReader;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.vpac.grisu.client.control.EnvironmentManager;
import org.vpac.grisu.client.control.exceptions.JobSubmissionException;
import org.vpac.grisu.client.control.files.FileTransfer;
import org.vpac.grisu.client.control.files.FileTransferEvent;
import org.vpac.grisu.client.control.files.FileTransferException;
import org.vpac.grisu.client.control.files.FileTransferListener;
import org.vpac.grisu.client.control.login.LoginException;
import org.vpac.grisu.client.control.login.LoginHelpers;
import org.vpac.grisu.client.model.files.GrisuFileObject;
import org.vpac.grisu.client.model.login.LoginParams;
import org.vpac.grisu.control.JobConstants;
import org.vpac.grisu.control.JobCreationException;
import org.vpac.grisu.control.ServiceInterface;
import org.vpac.grisu.control.SeveralStringHelpers;
import org.vpac.grisu.control.exceptions.JobDescriptionNotValidException;
import org.vpac.grisu.control.exceptions.NoSuchJobException;
import org.vpac.grisu.control.exceptions.ServiceInterfaceException;
import org.vpac.security.light.plainProxy.LocalProxy;
import org.globus.gsi.GlobusCredentialException;


public class Gricli implements FileTransferListener {
	
	private static Logger myLogger = Logger.getLogger(Gricli.class.getName());

	public static final int DEFAULT_SLEEP_TIME_IN_SECONDS = 600;

	public static final String JOBNAME_PLACEHOLDER = "XXX_JOBNAME_XXX";
	public static final String APPLICATION_NAME_PLACEHOLDER = "XXX_APPLICATION_NAME_XXX";
	public static final String EXECUTABLE_NAME_PLACEHOLDER = "XXX_EXECUTABLE_XXX";
	public static final String ARGUMENTS_PLACEHOLDER = "XXX_ARGUMENT_ELEMENTS_XXX";
	public static final String WORKINGDIRECTORY_PLACEHOLDER = "XXX_WORKINGDIRECTORY_XXX";
	public static final String STDOUT_PLACEHOLDER = "XXX_STDOUT_XXX";
	public static final String STDERR_PLACEHOLDER = "XXX_STDERR_XXX";
	public static final String MODULE_PLACEHOLDER = "XXX_MODULE_XXX";
	public static final String EMAIL_PLACEHOLDER = "XXX_EMAIL_ADDRESS_XXX";
	public static final String TOTALCPUTIME_PLACEHOLDER = "XXX_TOTALCPUTIME_XXX";
	public static final String TOTALCPUCOUNT_PLACEHOLDER = "XXX_TOTALCPUCOUNT_XXX";
	public static final String SUBMISSIONLOCATION_PLACEHOLDER = "XXX_SUBMISSIONLOCATION_XXX";
	public static final String USEREXECUTIONHOSTFS_PLACEHOLDER = "XXX_USEREXECUTIONHOSTFS";

	public static final String DEFAULT_MYPROXY_SERVER = "myproxy.arcs.org.au";
	public static final String DEFAULT_MYPROXY_PORT = "443";

	private GrisuClientProperties clientProperties = null;
	private JobProperties jobProperties = null;

	private ServiceInterface serviceInterface = null;
	private EnvironmentManager em = null;

	private boolean verbose = false;
	private boolean forced_all_mode = false;
	
	
	/**
	 * Use this constructor if you want to create a GrisuClient instance from scratch with everything needed
	 * using the commandline arguments that are specified in {@link GrisuClientCommandlineProperties} and {@link CommandlineProperties}.
	 * @param args the arguments
	 * @params password the password the password
	 * @throws ServiceInterfaceException if the client can't create a valid serviceInterface
	 * @throws LoginException if there is a problem with the login (e.g. wrong username/password)
	 */
	public Gricli(String[] args) throws LoginException, ServiceInterfaceException, IOException {

		clientProperties = new GrisuClientCommandlineProperties(args);

		verbose = clientProperties.verbose();
		enableDebug(clientProperties.debug());

		if (clientProperties.useLocalProxy()) {
			login();
		}
		else {
			ConsoleReader consoleReader = new ConsoleReader();
			char[] password = consoleReader.readLine(
								 "Please enter your myproxy password: ",
								 new Character('*')).toCharArray();
			login(clientProperties.getMyProxyUsername(), password);			
		}

		jobProperties = new CommandlineProperties(serviceInterface,
				((GrisuClientCommandlineProperties)clientProperties).getCommandLine());
	}
	
	/**
	 * Use this constructur if you want to reuse a serviceInterface/session.
	 * @param serviceInterface the serviceinterface
	 * @param args the commandlineArguments
	 */
	public Gricli(ServiceInterface serviceInterface, String[] args) {
		
		this.serviceInterface = serviceInterface;
		
		clientProperties = new GrisuClientCommandlineProperties(args);

		verbose = clientProperties.verbose();
		enableDebug(clientProperties.debug());
		
		jobProperties = new CommandlineProperties(serviceInterface, ((GrisuClientCommandlineProperties)clientProperties).getCommandLine());
		
		
	}
	
	/**
	 * Use this constructor if you want to control the client/job properties of this GrisuClient yourself. Useful if you want to write a multi-job control tool for example.
	 * @param serviceInterface the serviceInterface
	 * @param clientProperties the clientProperties
	 * @param jobProperties the jobProperties
	 */
	public Gricli(ServiceInterface serviceInterface, GrisuClientProperties clientProperties, JobProperties jobProperties) {
		
		this.serviceInterface = serviceInterface;
		this.clientProperties = clientProperties;
		this.jobProperties = jobProperties;
		
		verbose = clientProperties.verbose();
		enableDebug(clientProperties.debug());

		
	}


	private void enableDebug(boolean debug) {
		
		if ( debug ) {
			Level lvl = Level.toLevel("debug");
			Logger.getRootLogger().setLevel(lvl);
		} 
		
	}
	
	private EnvironmentManager getEnvironmentManager() {

		if (em == null) {
			em = new EnvironmentManager(serviceInterface);
		}
		return em;
	}

	private void login(String username, char[] password) throws LoginException,
			ServiceInterfaceException {

		if (verbose) {
			System.out.println("Login to grisu backend: "
					+ clientProperties.getServiceInterfaceUrl() + "...");
		}
		LoginParams loginParams = new LoginParams(clientProperties
				.getServiceInterfaceUrl(), username, password,
				DEFAULT_MYPROXY_SERVER, DEFAULT_MYPROXY_PORT);
		serviceInterface = LoginHelpers.login(loginParams);

		if (verbose) {
			System.out.println("Login successful.");
		}

	}

	private void login() throws LoginException,ServiceInterfaceException{
		try {
			if (verbose) {
				System.out.println("Login to grisu backend: "
						   + clientProperties.getServiceInterfaceUrl() + "...");
			}
			LoginParams loginParams = new LoginParams(clientProperties.getServiceInterfaceUrl(), null, null, 
								  DEFAULT_MYPROXY_SERVER, DEFAULT_MYPROXY_PORT);
			serviceInterface = LoginHelpers.login(loginParams,LocalProxy.loadGSSCredential());
			if (verbose) {
				System.out.println("Login successful.");
			}
		} catch (GlobusCredentialException e) {
			throw new LoginException(e.getMessage());
		}
	}

	public void start() throws ExecutionException {

		if (serviceInterface == null) {
			System.err
					.println("Could not find valid serviceInterface. Are you logged in?");
			System.exit(1);
		}

		try {
		String mode = clientProperties.getMode();
		if (GrisuClientCommandlineProperties.SUBMIT_MODE_PARAMETER.equals(mode)) {
			try {
				executeSubmission();
			} catch (Exception e) {
				myLogger.error(e);
				throw new ExecutionException("Couldn't submit job: "
						+ e.getLocalizedMessage(), e);
			}
		} else if (GrisuClientCommandlineProperties.STATUS_MODE_PARAMETER
				.equals(mode)) {

			int status = checkStatus();
			System.out.println("Status for job " + jobProperties.getJobname()
					+ ": " + JobConstants.translateStatus(status));

		} else if (GrisuClientCommandlineProperties.FORCE_CLEAN_MODE
				.equals(mode)) {

			killAndCleanJob();

		} else if (GrisuClientCommandlineProperties.JOIN_MODE_PARAMETER
				.equals(mode)) {

			joinJob();

		} else if (GrisuClientCommandlineProperties.ALL_MODE_PARAMETER.equals(mode)) {
			
			executeWholeJobsubmissionCycle();
			
		} else if (GrisuClientCommandlineProperties.FORCE_ALL_MODE_PARAMETER.equals(mode)) {
			
			forced_all_mode = true;
			executeWholeJobsubmissionCycle();
			
		}
		
		} catch (Exception e) {
			
			if ( e instanceof ExecutionException ) {
				myLogger.error(e);
				throw (ExecutionException)e;
			} else {
				myLogger.error(e);
				throw new ExecutionException(e);
			}
			
		} finally {
			try {
				if ( serviceInterface != null ) {
					serviceInterface.logout();
				}
			} catch (Exception e) {
				myLogger.error(e);
				System.err.println(e.getLocalizedMessage());
			}
		}

	}

	private int checkStatus() throws ExecutionException {

		try {
			int status = getStatus();
			String stringStatus = JobConstants.translateStatus(status);

			if (verbose) {
				System.out.println("Status for job "
						+ jobProperties.getJobname() + ": " + stringStatus);
			}

			if (clientProperties.stageOutResults() || forced_all_mode) {

				if (status == JobConstants.DONE || (status >= JobConstants.FINISHED_EITHER_WAY && forced_all_mode)) {

					if (verbose) {
						String stageoutdir = clientProperties.getStageoutDirectory();
						if (StringUtils.isEmpty(stageoutdir)) {
							try {
								stageoutdir = new File(".").getCanonicalPath();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
						System.out
								.println("Trying to stageout job directory to "
										+ stageoutdir + "...");
					}
					try {
						stageoutFiles();
						if ( verbose ) {
							System.out.println("Stageout finished successfully.");
						}
					} catch (FileTransferException e) {
						myLogger.error(e);
						throw new ExecutionException(
								"Couldn't stage out jobdirectory: "
										+ e.getLocalizedMessage(), e);
					}
					try{
						if ( clientProperties.cleanAfterStageOut() || forced_all_mode ) {
							if ( verbose ) {
								System.out.println("Deleting job & job directory...");
							}
							killAndCleanJob();
							if ( verbose ) {
								System.out.println("Job & job directory deleted.");
							}
						}
					} catch (Exception e) {
						myLogger.error(e);
						throw new ExecutionException(
								"Couldn't clean job & jobdirectory: "
										+ e.getLocalizedMessage(), e);
					}

				} else if (status >= JobConstants.FINISHED_EITHER_WAY) {

					if (verbose) {
						System.out
								.println("Didn't stageout files since either job failed or didn't finish with exit code 0.");
					}

				}
			}

			return status;

		} catch (NoSuchJobException e) {
			myLogger.error(e);
			throw new ExecutionException("Couldn't find job with jobname \""
					+ jobProperties.getJobname() + "\".", e);
		}
	}

	private void executeWholeJobsubmissionCycle() throws ExecutionException {

		try {
			executeSubmission();
		} catch (Exception e) {
			myLogger.error(e);
			throw new ExecutionException("Couldn't submit job: "
					+ e.getLocalizedMessage(), e);
		}

		joinJob();

	}

	private void joinJob() throws ExecutionException {

		int status = -1;
		int sleepTime = clientProperties.getRecheckInterval();
		if (sleepTime <= 0) {
			sleepTime = DEFAULT_SLEEP_TIME_IN_SECONDS;
		}
		do {

			status = checkStatus();

			if (status < JobConstants.FINISHED_EITHER_WAY) {
				try {
					Thread.sleep(sleepTime * 1000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

		} while (status < JobConstants.FINISHED_EITHER_WAY);

	}

	private int getStatus() {

		int status = serviceInterface.getJobStatus(jobProperties.getJobname());

		return status;
	}

	private void killAndCleanJob() throws ExecutionException {

		try {
			if (verbose) {
				System.out
						.println("Trying to kill possibly existing job with jobname \""
								+ jobProperties.getJobname() + "\"");
			}
			serviceInterface.kill(jobProperties.getJobname(), true);
			if (verbose) {
				System.out.println("Killed and cleaned existing job \""
						+ jobProperties.getJobname() + "\"");
			}
		} catch (NoSuchJobException e) {
			// that's ok
			if (verbose) {
				System.out.println("No job killed because no job with jobname "
						+ jobProperties.getJobname() + " existed.");
			}
		} catch (Exception e) {
			myLogger.error(e);
			throw new ExecutionException(
					"Can't kill & clean existing job with jobname \""
							+ jobProperties.getJobname() + "\"");
		}

	}

	private void executeSubmission() throws JobCreationException,
			JobDescriptionNotValidException, NoSuchJobException,
			JobSubmissionException, JobStagingException {

		InputStream in = Gricli.class
				.getResourceAsStream("/templates/generic.xml");
		String jsdlTemplateString = SeveralStringHelpers.fromInputStream(in);

//		// this is a workaround because of a hsqldb bug which will cause the whole process to fail
//		// for some reason if a job is submitted directly
//		int status = serviceInterface.getJobStatus("nonexistentJobca");
		
		if (clientProperties.killPossiblyExistingJob()) {
			try {
				if (verbose) {
					System.out
							.println("Trying to kill possibly existing job with jobname \""
									+ jobProperties.getJobname() + "\"");
				}
				serviceInterface.kill(jobProperties.getJobname(), true);
				if (verbose) {
					System.out.println("Killed and cleaned existing job \""
							+ jobProperties.getJobname() + "\"");
				}
			} catch (NoSuchJobException e) {
				// that's ok
				if (verbose) {
					System.out
							.println("No job killed because no job with jobname "
									+ jobProperties.getJobname() + " existed.");
				}
			} catch (Exception e) {
				myLogger.error(e);
				throw new JobCreationException(
						"Can't kill & clean existing job with jobname \""
								+ jobProperties.getJobname() + "\"");
			}
		}

		if (verbose) {
			System.out.println("Preparing job description...");
		}

		String jsdl = jsdlTemplateString.replaceAll(JOBNAME_PLACEHOLDER,
				jobProperties.getJobname());
		jsdl = jsdl.replaceAll(APPLICATION_NAME_PLACEHOLDER, jobProperties
				.getApplicationName());
		jsdl = jsdl.replaceAll(EXECUTABLE_NAME_PLACEHOLDER, jobProperties
				.getExecutablesName());
		String[] args = jobProperties.getArguments();
		StringBuffer argElements = new StringBuffer();
		for (String arg : args) {
			argElements.append("<Argument>" + arg + "</Argument>\n");
		}
		jsdl = jsdl.replaceAll(ARGUMENTS_PLACEHOLDER, argElements.toString());
		jsdl = jsdl.replaceAll(WORKINGDIRECTORY_PLACEHOLDER, jobProperties
				.getWorkingDirectory());
		jsdl = jsdl.replaceAll(STDOUT_PLACEHOLDER, jobProperties.getStdout());
		jsdl = jsdl.replaceAll(STDERR_PLACEHOLDER, jobProperties.getStderr());
		jsdl = jsdl.replaceAll(MODULE_PLACEHOLDER, jobProperties.getModule());
		jsdl = jsdl.replaceAll(EMAIL_PLACEHOLDER, jobProperties
				.getEmailAddress());
		int noCpus = jobProperties.getNoCPUs();
		int cpuTime = jobProperties.getWalltimeInSeconds() * noCpus;
		jsdl = jsdl.replaceAll(TOTALCPUTIME_PLACEHOLDER, new Integer(cpuTime)
				.toString());
		jsdl = jsdl.replaceAll(TOTALCPUCOUNT_PLACEHOLDER, new Integer(noCpus)
				.toString());
		jsdl = jsdl.replaceAll(SUBMISSIONLOCATION_PLACEHOLDER, jobProperties
				.getSubmissionLocation());
		jsdl = jsdl.replaceAll(USEREXECUTIONHOSTFS_PLACEHOLDER, jobProperties
				.getUserExecutionHostFs());

		if (verbose) {
			System.out.println("Job description prepared:");
			System.out.println(jsdl);
			System.out.println("\nCreating job on grisu backend...");
		}

		String jobname = serviceInterface.createJob(jobProperties.getJobname(),
				JobConstants.DONT_ACCEPT_NEW_JOB_WITH_EXISTING_JOBNAME);
		if (verbose) {
			System.out.println("Job created.");
			System.out.println("Setting job description...");
		}
		serviceInterface.setJobDescription_string(jobname, jsdl);

		if (verbose) {
			System.out.println("Job description set.");
		}

		String inputFiles = null;
		String[] inputFilesStrings = jobProperties.getInputFiles();
		if (inputFilesStrings.length > 0) {
			if (verbose) {
				System.out.println("Uploading input files:");
			}
			inputFiles = stageInputFiles(inputFilesStrings, jobProperties
					.getAbsoluteJobDir());

			if (verbose) {
				System.out.println("Upload successful.");
			} else {
				if (verbose) {
					System.out.println("No files to stagein.");
				}
			}
		}
		if (verbose) {
			System.out.println("Submitting job...");
		}
		try {
			serviceInterface.submitJob(jobname, jobProperties.getVO());
		} catch (Exception e) {
			myLogger.error(e);
			throw new JobSubmissionException("Job submission failed: "
					+ e.getLocalizedMessage(), e);
		}
		if (verbose) {
			System.out.println("Job submitted.");
			System.out
					.println("Adding job properties for this job on the grisu backend...");
		}

		// TODO not sure what to do here, this is because the jobproperties
		// value in the db is a varchar(2000)
		if (!StringUtils.isEmpty(inputFiles) && inputFiles.length() <= 253) {
			serviceInterface.addJobProperty(jobname,
					JobConstants.JOBPROPERTYKEY_INPUTFILES, inputFiles);
		}

		if (verbose) {
			System.out.println("Job submission finished successfully.");
		}
	}

	/**
	 * Uploads local files via the grisu backend to a target directory.
	 * 
	 * @param uris
	 *            the uris of the local files to upload
	 * @param targetDirectory
	 *            the target directory
	 * @return all the urls of the staged files, seperated with a comma
	 * @throws JobCreationException
	 */
	public String stageInputFiles(String[] uris, String targetDirectory)
			throws JobStagingException {

		StringBuffer inputFiles = new StringBuffer();
		for (String uri : uris) {

			DataSource dataSource = null;
			String fileName = null;
			try {
				File file = new File(new URI(uri));
				
				if ( ! file.exists() ) {
					throw new JobStagingException("Local file "+uri+" does not exist.");
				}
				
				dataSource = new FileDataSource(file);
				fileName = file.getName();
			} catch (URISyntaxException e) {
				throw new JobStagingException("Couldn't stage in file: " + uri
						+ ": Wrong uri format.", e);
			}

			try {
				if (verbose) {
					System.out.println("Uploading file " + fileName + " to "
							+ targetDirectory);
				}
				String targetFile = serviceInterface.upload(dataSource,
						targetDirectory + "/" + fileName, true);
				inputFiles.append(targetFile + ",");
			} catch (Exception e) {
				throw new JobStagingException("Couldn't stage in file: " + uri
						+ ": " + e.getLocalizedMessage(), e);
			}
		}

		return inputFiles.toString();
	}

	public void stageoutFiles() throws NoSuchJobException,
			FileTransferException {

		if (verbose) {
			System.out
					.println("Getting job details to find out url of jobdirectory...");
		}

		Map<String, String> jobDetails = serviceInterface
				.getAllJobProperties(jobProperties.getJobname());

		String jobDirectory = jobDetails
				.get(JobConstants.JOBPROPERTYKEY_JOBDIRECTORY);

		GrisuFileObject source = null;
		try {
			source = getEnvironmentManager().getFileManager().getFileObject(
					jobDirectory);
		} catch (URISyntaxException e) {
			throw new FileTransferException("Could not transfer jobdirectory "
					+ jobDirectory + ": " + e.getLocalizedMessage());
		}

		File stageOutDir = null;
		String stageoutDirPath = clientProperties.getStageoutDirectory();

		if (StringUtils.isEmpty(stageoutDirPath)) {
			stageOutDir = new File(".");
		} else {
			stageOutDir = new File(stageoutDirPath);
		}

		GrisuFileObject target = null;
		target = getEnvironmentManager().getFileManager().getFileObject(
				stageOutDir.toURI());

		FileTransfer transfer = new FileTransfer(
				new GrisuFileObject[] { source }, target,
				FileTransfer.DONT_OVERWRITE);
		transfer.addListener(this);
		transfer.startTransfer(true);

	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		Gricli client = null;

		try {
			client = new Gricli(args);
		} catch (LoginException e) {
			System.err.println("Can't login to grisu backend: "
					+ e.getLocalizedMessage());
			System.exit(1);
		} catch (ServiceInterfaceException e) {
			System.err.println("Error with serviceInterface: "
					+ e.getLocalizedMessage());
			System.exit(1);
		}
		catch (IOException e) {
			System.err.println("Could not read password input: "
					   + e.getLocalizedMessage());
			System.exit(1);
		}


		try {
			client.start();
		} catch (ExecutionException e) {
			myLogger.error(e);
			System.err.println("Can't execute command: "
					+ e.getLocalizedMessage());
			System.exit(1);
		}

	}

	public void fileTransferEventOccured(FileTransferEvent e) {

		if (verbose) {
			System.out.println(e.getTransfer().getLatestTransferMessage());
		}

	}

}
