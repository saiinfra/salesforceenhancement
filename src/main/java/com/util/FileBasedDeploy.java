package com.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import com.domain.EnvironmentDO;
import com.domain.MetaBean;
import com.ds.salesforce.dao.comp.EnvironmentDAO;
import com.exception.SFErrorCodes;
import com.exception.SFException;
import com.google.common.primitives.Bytes;
import com.services.component.FDGetSFoAuthHandleService;
import com.sforce.soap.metadata.AsyncResult;
import com.sforce.soap.metadata.CodeCoverageWarning;
import com.sforce.soap.metadata.DeployDetails;
import com.sforce.soap.metadata.DeployMessage;
import com.sforce.soap.metadata.DeployOptions;
import com.sforce.soap.metadata.DeployResult;
import com.sforce.soap.metadata.MetadataConnection;
import com.sforce.soap.metadata.RunTestFailure;
import com.sforce.soap.metadata.RunTestsResult;
import com.sforce.soap.metadata.TestLevel;
import com.sforce.ws.ConnectionException;

/**
 * Sample that logs in and shows a menu of retrieve and deploy metadata options.
 */
public class FileBasedDeploy {

	// one second in milliseconds
	private static final long ONE_SECOND = 1000;

	// maximum number of attempts to deploy the zip file
	private static final int MAX_NUM_POLL_REQUESTS = 50;

	int count = 0;

	public static void main(String[] args) throws Exception {
		FileBasedDeploy fbDeploy = new FileBasedDeploy();
		// fbDeploy.deploy();
	}

	public FileBasedDeploy() {
	}

	public void deploy(String bOrgId, String bOrgToken, String bOrgURL,
			String refreshToken, SFoAuthHandle sfHandle, String packageName,
			boolean isValidate, String metatadataLogId, String testlevel,
			List<Object> deployList1) throws Exception {
		count++;
		FDGetSFoAuthHandleService fdGetSFoAuthHandleService = new FDGetSFoAuthHandleService();

		SFoAuthHandle sfBaseHandle = fdGetSFoAuthHandleService
				.getSFoAuthHandle(bOrgId, bOrgToken, bOrgURL, refreshToken,
						Constants.BaseOrgID);

		byte zipBytes[] = readZipFile(metatadataLogId);
		DeployOptions deployOptions = new DeployOptions();
		deployOptions.setPerformRetrieve(false);
		deployOptions.setRollbackOnError(true);
	    String[] tests = null;
		if (isValidate) {

			deployOptions.setCheckOnly(true);
			if (testlevel.equals("RunLocalTests")) {
				deployOptions.setTestLevel(TestLevel.RunLocalTests);
			}
			if (testlevel.equals("NoTestRun")) {
				deployOptions.setTestLevel(TestLevel.NoTestRun);

			}
			if (testlevel.equals("RunSpecifiedTests")) {
				deployOptions.setTestLevel(TestLevel.RunSpecifiedTests);
				tests = new String[deployList1.size()];
				int count = 0;
				for (Iterator iterator = deployList1.iterator(); iterator
						.hasNext();) {
					MetaBean metaBean = (MetaBean) iterator.next();
					tests[count] = metaBean.getName();
					count++;
				}
				// Add the test class names array to the deployment options.
				deployOptions.setRunTests(tests);

			}
			if (testlevel.equals("RunAllTestsInOrg")) {
				deployOptions.setTestLevel(TestLevel.RunAllTestsInOrg);

			} 

		}
		MetadataConnection conn = null;
		AsyncResult asyncResult = null;
		try {
			conn = sfHandle.getMetadataConnection();
		} catch (Exception e) {
			// e.printStackTrace();
			System.out.println(e.toString());
			throw new SFException("Error related to " + packageName
					+ " Details: " + e.toString(),
					SFErrorCodes.Metadata_Conn_Error);
		}
		try {
			conn = sfHandle.getMetadataConnection();
		} catch (Exception e) {
			// e.printStackTrace();
			System.out.println(e.toString());
			throw new SFException("Error related to " + packageName
					+ " Details: " + e.toString(),
					SFErrorCodes.Metadata_Conn_Error);
		}
		try {
			asyncResult = conn.deploy(zipBytes, deployOptions);
		} catch (ConnectionException e) {
			// e.printStackTrace();
			System.out.println(e.toString());
			throw new SFException("Error related to " + packageName
					+ " Details: " + e.toString(), SFErrorCodes.SF_Conn_Error);
		}

		DeployResult result = waitForDeployCompletion(sfHandle,
				asyncResult.getId());
		if (!result.isSuccess()) {
			XMLUtil.doPreProcessing(metatadataLogId);

			String errors = printErrors(result, "Final list of failures:\n");

			throw new SFException("Error related to " + packageName
					+ " Details: " + errors, SFErrorCodes.FileDeploy_Error);
			// throw new Exception("The files were not successfully deployed");
		}

		System.out.println("The file " + metatadataLogId
				+ Constants.Component_Zip_FileName
				+ " was successfully deployed\n");
		System.out.println("count is" + count);

		EnvironmentDAO environmentDAO = new EnvironmentDAO();
		List<Object> envList = environmentDAO.findById(sfHandle.getOrgId(),
				sfBaseHandle);
		String password = "Welcome@1";
		// check whether version control yes or no

		for (Iterator<Object> iterator = envList.iterator(); iterator.hasNext();) {
			EnvironmentDO env = (EnvironmentDO) iterator.next();

			if (env.getEnableVersionControl().equals("Yes")) {
				// /get git URL from env ,clone and add deployed files to get
				// and commit

				System.out
						.println("Verion Control is Yes We can now Check in the files");
				System.out.println("Git URL :" + env.getGitServerURL());
				System.out.println("Git USERNAME :" + env.getGitUsername());

				System.out.println("GIT PASSWORD" + password);

				GitRepoDO gitRepoDO = new GitRepoDO(env.getGitUsername(),
						password, env.getGitServerURL().trim());
				RepoUtil.CheckIn(gitRepoDO, metatadataLogId);
				// XMLUtil.doPreProcessing(metatadataLogId);

			} else {
				// XMLUtil.doPreProcessing(metatadataLogId);
			}

		}

	}

	public static void readFile(String metadataLogId) {

		try {
			ZipFile zf = new ZipFile(metadataLogId + "/"
					+ Constants.Component_Zip_FileName);
			Enumeration entries = zf.entries();

			while (entries.hasMoreElements()) {
				ZipEntry ze = (ZipEntry) entries.nextElement();
				File folders = null;
				String extension = ze.getName().substring(
						ze.getName().lastIndexOf(".") + 1,
						ze.getName().length());
				if (!ze.getName().equals("unpackaged/package.xml")) {
					System.out.println("Read " + ze.getName());

					File file = new File("checkout");
					// File file = new File("checkout/" + ze.getName());
					if (!file.exists()) {

						if (file.mkdir()) {
							System.out.println("Directory is created!");
						} else {
							System.out.println("Failed to create directory!");
						}
					}

					StringTokenizer st = new StringTokenizer(ze.getName(), "/");
					File files = null;
					// unpackaged/classes/A.apex

					if (st.hasMoreTokens()) {

						String t1 = st.nextToken();
						String t2 = st.nextToken();
						String t3 = st.nextToken();
						String t4 = null;
						if (extension.equals("report")
								|| extension.equals("dashboard")
								|| extension.equals("email")) {
							files = new File("checkout/" + t1 + "/" + t2 + "/"
									+ t3);

							t4 = st.nextToken();
						}

						else {
							files = new File("checkout/" + t1 + "/" + t2);
						}
						if (!files.exists()) {
							if (files.mkdirs()) {
								System.out
										.println("Multiple directories are created!");
							} else {
								System.out
										.println("Failed to create multiple directories!");
							}
						}

						if (extension.equals("report")
								|| extension.equals("dashboard")
								|| extension.equals("email")) {
							folders = new File("checkout/" + t1 + "/" + t2
									+ "/" + t3 + "/" + t4);

						} else {
							folders = new File("checkout/" + t1 + "/" + t2
									+ "/" + t3);
						}
						if (!folders.exists()) {
							try {
								folders.createNewFile();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}

					}
					System.out.println("Done");

					long size = ze.getSize();
					if (size > 0) {
						System.out.println("Length is " + size);
						FileWriter fw = new FileWriter(
								folders.getAbsoluteFile());
						BufferedWriter bw = new BufferedWriter(fw);
						BufferedReader br = new BufferedReader(
								new InputStreamReader(zf.getInputStream(ze)));
						String line;
						while ((line = br.readLine()) != null) {
							bw.write(line);
							System.out.println(line);
						}
						br.close();
						bw.close();

					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/*
	 * Read the zip file contents into a byte array.
	 */private byte[] readZipFile(String metadataLogId) throws SFException {
		byte[] result = null;
		// We assume here that you have a deploy.zip file. // See the retrieve
		// sample for how to retrieve a zip file.
		File zipFile = new File(metadataLogId + "/"
				+ Constants.Component_Zip_FileName);
		if (!zipFile.exists() || !zipFile.isFile()) {
			throw new SFException(
					"Cannot find the zip file for deploy() on path:"
							+ zipFile.getAbsolutePath(),
					SFErrorCodes.File_Error);
		}
		FileInputStream fileInputStream = null;
		try {
			fileInputStream = new FileInputStream(zipFile);
		} catch (Exception e) {
			throw new SFException(e.toString(), SFErrorCodes.File_Error);
		}
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			byte[] buffer = new byte[4096];
			int bytesRead = 0;
			while (-1 != (bytesRead = fileInputStream.read(buffer))) {
				bos.write(buffer, 0, bytesRead);
			}

			result = bos.toByteArray();
		} catch (Exception e) {
			throw new SFException(e.toString(), SFErrorCodes.File_Error);
		} finally {
			try {
				fileInputStream.close();
			} catch (Exception e) {
				throw new SFException(e.toString(), SFErrorCodes.File_Error);
			}
		}
		return result;
	}

	/*
	 * Print out any errors, if any, related to the deploy.
	 * 
	 * @param result - DeployResult
	 */
	private String printErrors(DeployResult result, String messageHeader) {
		DeployDetails details = result.getDetails();
		StringBuilder stringBuilder = new StringBuilder();
		if (details != null) {
			DeployMessage[] componentFailures = details.getComponentFailures();
			for (DeployMessage failure : componentFailures) {
				String loc = "(" + failure.getLineNumber() + ", "
						+ failure.getColumnNumber();
				if (loc.length() == 0
						&& !failure.getFileName().equals(failure.getFullName())) {
					loc = "(" + failure.getFullName() + ")";
				}
				stringBuilder.append(
						failure.getFileName() + loc + ":"
								+ failure.getProblem()).append('\n');
			}
			RunTestsResult rtr = details.getRunTestResult();
			if (rtr.getFailures() != null) {
				for (RunTestFailure failure : rtr.getFailures()) {
					String n = (failure.getNamespace() == null ? "" : (failure
							.getNamespace() + ".")) + failure.getName();
					stringBuilder.append("Test failure, method: " + n + "."
							+ failure.getMethodName() + " -- "
							+ failure.getMessage() + " stack "
							+ failure.getStackTrace() + "\n\n");
				}
			}
			if (rtr.getCodeCoverageWarnings() != null) {
				for (CodeCoverageWarning ccw : rtr.getCodeCoverageWarnings()) {
					stringBuilder.append("Code coverage issue");
					if (ccw.getName() != null) {
						String n = (ccw.getNamespace() == null ? "" : (ccw
								.getNamespace() + ".")) + ccw.getName();
						stringBuilder.append(", class: " + n);
					}
					stringBuilder.append(" -- " + ccw.getMessage() + "\n");
				}
			}
		}
		if (stringBuilder.length() > 0) {
			stringBuilder.insert(0, messageHeader);
			System.out.println(stringBuilder.toString());
		}
		return stringBuilder.toString();
	}

	private DeployResult waitForDeployCompletion(SFoAuthHandle sfHandle,
			String asyncResultId) throws SFException {
		int poll = 0;
		long waitTimeMilliSecs = ONE_SECOND;
		DeployResult deployResult;
		boolean fetchDetails;
		MetadataConnection conn = null;
		try {
			conn = sfHandle.getMetadataConnection();
		} catch (Exception e) {
			// e.printStackTrace();
			System.out.println(e.toString());
			throw new SFException(e.toString(),
					SFErrorCodes.Metadata_Conn_Error);
		}
		try {
			do {
				Thread.sleep(waitTimeMilliSecs);
				// double the wait time for the next iteration

				waitTimeMilliSecs *= 2;
				if (poll++ > MAX_NUM_POLL_REQUESTS) {
					throw new SFException(
							"Request timed out. If this is a large set of metadata components, "
									+ "ensure that MAX_NUM_POLL_REQUESTS is sufficient.",
							SFErrorCodes.FileDeploy_Request_timed_out_Error);
				}
				// Fetch in-progress details once for every 3 polls
				fetchDetails = (poll % 3 == 0);

				deployResult = conn.checkDeployStatus(asyncResultId,
						fetchDetails);
				System.out.println("Status is: " + deployResult.getStatus());
				if (!deployResult.isDone() && fetchDetails) {
					printErrors(deployResult,
							"Failures for deployment in progress:\n");
				}
			} while (!deployResult.isDone());
		} catch (Exception e) {
			throw new SFException(
					"Request timed out. If this is a large set of metadata components, "
							+ "ensure that MAX_NUM_POLL_REQUESTS is sufficient.",
					SFErrorCodes.FileDeploy_Request_timed_out_Error);
		}
		try {
			if (!deployResult.isSuccess()
					&& deployResult.getErrorStatusCode() != null) {
				throw new SFException(deployResult.getErrorStatusCode()
						+ " msg: " + deployResult.getErrorMessage(),
						SFErrorCodes.FileDeploy_Error);
			}
		} catch (Exception e) {
			throw new SFException(
					"Request timed out. If this is a large set of metadata components, "
							+ "ensure that MAX_NUM_POLL_REQUESTS is sufficient.",
					SFErrorCodes.FileDeploy_Request_timed_out_Error);
		}
		if (!fetchDetails) {
			// Get the final result with details if we didn't do it in the last
			// attempt.
			try {
				deployResult = conn.checkDeployStatus(asyncResultId, true);
			} catch (ConnectionException e) {
				// e.printStackTrace();
				System.out.println(e.toString());
				throw new SFException(e.toString(), SFErrorCodes.SF_Conn_Error);
			}
		}
		return deployResult;
	}

	public static String getCurrentPath() {
		Path currentRelativePath = Paths.get("");
		String path = currentRelativePath.toAbsolutePath().toString();
		return path;
	}

}