/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.org.openeyes.diagnostics;

import java.io.IOException;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;

/**
 *
 */
public class HttpTransfer {

	private String host;
	private int port;
	/**
	 *
	 */
	private String response;
	private Header location;

	/**
	 *
	 * @return
	 */
	public String getResponse() {
		return response;
	}

	public String getLocation() {
		return location.getValue();
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	/**
	 *
	 * @param host
	 * @param port
	 * @param resourceType
	 * @param data
	 * @return
	 */
	public int send(String resourceType, String data) {
		int result = -1;
		String strURL = "http://" + host + ":" + port + "/api/"
				+ resourceType + "?_format=xml&resource_type="
				+ resourceType;
		PostMethod post = new PostMethod(strURL);
		try {
			data = data.replace("fhir:", "");
			StringRequestEntity requestEntity = new StringRequestEntity(data);
			post.setRequestEntity(requestEntity);
			post.setRequestHeader("Content-type",
					"text/xml");
			HttpClient httpclient = new HttpClient();

			result = httpclient.executeMethod(post);
			this.response = post.getResponseBodyAsString();
			this.location = post.getResponseHeader("Location");

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			post.releaseConnection();
		}
		return result;
	}

	/**
	 *
	 * @param resourceType
	 * @param requestParams
	 * @return
	 */
	public int read(String resourceType, String jsonType, String requestParams) {
		int result = -1;
		String strURL = "http://" + host + ":" + port + "/api/" + resourceType + "?resource_type=Patient&_format=xml";
		if (requestParams != null) {
			strURL += "&" + requestParams;
		}
		GetMethod get = new GetMethod(strURL);
		try {
			get.setRequestHeader("Content-type",
					"text/xml");
			HttpClient httpclient = new HttpClient();

			result = httpclient.executeMethod(get);
			this.response = get.getResponseBodyAsString();

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			get.releaseConnection();
		}
		return result;
	}
}
