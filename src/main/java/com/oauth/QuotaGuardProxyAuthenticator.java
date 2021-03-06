package com.oauth;

import java.net.Authenticator;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;

public class QuotaGuardProxyAuthenticator extends Authenticator {
	private String user, password, host;
	private int port;
	private ProxyAuthenticator auth;

	public QuotaGuardProxyAuthenticator() {
		String proxyUrlEnv = "http://quotaguard4659:aaaac128b3b4@us-east-1-static-hopper.quotaguard.com:9293";
		if (proxyUrlEnv != null) {
			try {
				URL proxyUrl = new URL(proxyUrlEnv);
				String authString = proxyUrl.getUserInfo();
				user = authString.split(":")[0];
				password = authString.split(":")[1];
				host = proxyUrl.getHost();
				port = proxyUrl.getPort();
				System.out.println("User "+user +"Password :"+password);
				auth = new ProxyAuthenticator(user, password);
				setProxy();
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
		} else {
			System.err
					.println("You need to set the environment variable QUOTAGUARDSTATIC_URL!");
		}

	}

	private void setProxy() {
		System.setProperty("http.proxyHost", host);
		System.setProperty("http.proxyPort", String.valueOf(port));
		System.setProperty("https.proxyHost", host);
		System.setProperty("https.proxyPort", String.valueOf(port));
	}

	/*public String getEncodedAuth() {
		// If not using Java8 you will have to use another Base64 encoded, e.g.
		// apache commons codec.
		String encoded = java.util.Base64.getEncoder().encodeToString(
				(user + ":" + password).getBytes());
		return encoded;
	}*/

	public ProxyAuthenticator getAuth() {
		return auth;
	}

	class ProxyAuthenticator extends Authenticator {

		private String user, password;

		public ProxyAuthenticator(String user, String password) {
			this.user = user;
			this.password = password;
		}

		protected PasswordAuthentication getPasswordAuthentication() {
			return new PasswordAuthentication(user, password.toCharArray());
		}
	}

}