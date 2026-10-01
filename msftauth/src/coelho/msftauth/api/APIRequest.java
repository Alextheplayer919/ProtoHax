package coelho.msftauth.api;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.HttpEntityWrapper;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public abstract class APIRequest<R> {

	/**
	 * whether a non-200 status is fatal. The default transport behaviour is to
	 * throw; SISU authorize overrides this to keep the response (XBL answers
	 * 403 + WebPage when phone verification is required).
	 */
	protected boolean isFatalNon200() {
		return true;
	}

	public R request() throws Exception {
		RequestConfig clientConfig = RequestConfig.custom()
				.setConnectionRequestTimeout(10_000)
				.setConnectTimeout(10_000)
				.setSocketTimeout(10_000)
				.build();
		try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(clientConfig).build()) {
			return request(client);
		}
	}

	public R request(CloseableHttpClient client) throws Exception {
		HttpUriRequest request = buildRequest();
		try (CloseableHttpResponse response = client.execute(request)) {
			return decode(response);
		}
	}

	public R request(OkHttpClient client) throws Exception {
		// build the httpcore request (same encoding/headers/signing) and replay it over OkHttp
		HttpUriRequest request = buildRequest();

		RequestBody body = null;
		if (request instanceof HttpPost && ((HttpPost) request).getEntity() != null) {
			body = RequestBody.create(EntityUtils.toByteArray(((HttpPost) request).getEntity()));
		}

		Request.Builder builder = new Request.Builder()
				.url(request.getURI().toString());
		for (Header header : request.getAllHeaders()) {
			builder.addHeader(header.getName(), header.getValue());
		}
		String method = request.getMethod();
		if ("GET".equalsIgnoreCase(method)) {
			builder.get();
		} else if ("POST".equalsIgnoreCase(method)) {
			builder.post(body != null ? body : RequestBody.create(new byte[0]));
		} else {
			throw new IllegalStateException("unsupported http method: " + method);
		}

		try (okhttp3.Response response = client.newCall(builder.build()).execute()) {
			return decode(new OkHttpResponse(response));
		}
	}

	private HttpUriRequest buildRequest() {
		HttpUriRequest request;
		if (this.getRequestEncoding() != null) {
			HttpPost post = new HttpPost(this.getHttpURL());
			this.getRequestEncoding().encode(post, this);
			request = post;
		} else {
			request = new HttpGet(this.getHttpURL());
		}
		if (this.getHttpAuthorization() != null) {
			request.setHeader("Authorization", this.getHttpAuthorization());
		}
		this.applyHeader(request);
		return request;
	}

	private R decode(HttpResponse response) throws Exception {
		int status = response.getStatusLine().getStatusCode();
		if (status != 200) {
			if (response.getEntity() != null) {
				try {
					System.out.println(EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
				} catch (Exception ignore) {
				}
			}
			if (isFatalNon200()) {
				throw new IllegalStateException("status code: " + status);
			}
		}
		R decoded = this.getResponseEncoding().decode(response, this.getResponseClass());
		if (decoded instanceof APIResponseExt) {
			((APIResponseExt) decoded).applyResponse(response);
		}
		return decoded;
	}

	public void applyHeader(HttpRequest request) {

	}

	public abstract String getHttpURL();

	public String getHttpAuthorization() {
		return null;
	}

	public abstract APIEncoding getRequestEncoding();

	public abstract APIEncoding getResponseEncoding();

	public abstract Class<R> getResponseClass();

}
