/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.org.openeyes.diagnostics;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.ConnectException;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

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
	 * @param resourceType
	 * @param data
	 * @param username
	 * @param password
	 * @return
	 * @throws ConnectException 
	 */
	public int send(String resourceType, String data, String username,
			String password) throws ConnectException {
		int result = -1;
		String strURL = "http://" + host + ":" + port + "/api/"
				+ resourceType + "?_format=xml&resource_type="
				+ resourceType;
		HttpPost post = new HttpPost(strURL);
		try {
			data = data.replace("fhir:", "");
			StringEntity entity = new StringEntity(data);
			post.setEntity(entity);
			post.addHeader("Content-type", "text/xml");
			UsernamePasswordCredentials creds = new UsernamePasswordCredentials(
					username, password);
			post.addHeader(BasicScheme.authenticate(creds, "US-ASCII", false));
			DefaultHttpClient httpclient = new DefaultHttpClient();

			CloseableHttpResponse httpResponse = httpclient.execute(post);
			result = httpResponse.getStatusLine().getStatusCode();
			HttpEntity entity2 = httpResponse.getEntity();
                        
			StringWriter writer = new StringWriter();
			IOUtils.copy(entity2.getContent(), writer);
			this.response = writer.toString();
			EntityUtils.consume(entity2);
			Header[] headers = post.getHeaders("Location");
			if (headers.length > 0) {
				this.location = headers[0];
			}

		} catch (ConnectException e) {
			// TODO - binary exponential backoff algorithm  
			throw e;
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
	 * @param jsonType
	 * @param requestParams
	 * @param username
	 * @param password
	 * @return
	 * @throws ConnectException 
	 */
	public int read(String resourceType, String jsonType, String requestParams,
			String username, String password)
			throws ConnectException {
		DefaultHttpClient http = new DefaultHttpClient();

		int result = -1;
		String strURL = "http://" + host + ":" + port + "/api/"
				+ resourceType + "?resource_type=Patient&_format=xml";
		if (requestParams != null) {
			strURL += "&" + requestParams;
		}
		HttpGet get = new HttpGet(strURL);
		UsernamePasswordCredentials creds = new UsernamePasswordCredentials(
				username, password);
		get.addHeader(BasicScheme.authenticate(creds, "US-ASCII", false));

		try {
			get.addHeader("Content-type", "text/xml");
			HttpClientBuilder builder = HttpClientBuilder.create();
			CloseableHttpClient httpclient = builder.build();

			CloseableHttpResponse httpResponse = httpclient.execute(get);
			result = httpResponse.getStatusLine().getStatusCode();
			HttpEntity entity2 = httpResponse.getEntity();
			StringWriter writer = new StringWriter();
			IOUtils.copy(entity2.getContent(), writer);
			this.response = writer.toString();
			EntityUtils.consume(entity2);
		} catch (ConnectException e) {
			// this happens when there's no server to connect to
                    e.printStackTrace();
			throw e;
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			get.releaseConnection();
		}
		return result;
	}
}
