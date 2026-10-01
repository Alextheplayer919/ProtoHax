package coelho.msftauth.api;

import okhttp3.Response;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHeaderIterator;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * exposes an OkHttp response with the httpcore 4.4.13 {@link HttpResponse} /
 * {@link org.apache.http.HttpMessage} surface so the existing
 * {@link APIResponseExt} / {@link APIEncoding} decode paths work unchanged on
 * the OkHttp transport.
 */
public class OkHttpResponse implements HttpResponse {

	private final Response response;
	private final StatusLine statusLine;
	private HttpEntity entity;

	public OkHttpResponse(Response response) {
		this.response = response;
		this.statusLine = new BasicStatusLine(new ProtocolVersion("HTTP", 1, 1), response.code(), response.message());
	}

	@Override
	public StatusLine getStatusLine() {
		return statusLine;
	}

	@Override
	public void setStatusLine(StatusLine statusLine) {
		throw new UnsupportedOperationException();
	}

	@Override
	public HttpEntity getEntity() {
		if (entity == null) {
			try {
				okhttp3.ResponseBody body = response.body();
				byte[] bytes = body == null ? new byte[0] : body.bytes();
				String contentType = response.header("Content-Type");
				entity = new ByteArrayEntity(bytes, contentType == null ? null : ContentType.parse(contentType));
			} catch (IOException e) {
				throw new IllegalStateException("failed to read response body", e);
			}
		}
		return entity;
	}

	@Override
	public void setEntity(HttpEntity entity) {
		this.entity = entity;
	}

	@Override
	public void setStatusLine(ProtocolVersion version, int sc) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setStatusLine(ProtocolVersion version, int sc, String reason) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setStatusCode(int sc) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setReasonPhrase(String reason) {
		throw new UnsupportedOperationException();
	}

	@Override
	public java.util.Locale getLocale() {
		return null;
	}

	@Override
	public void setLocale(java.util.Locale locale) {
		throw new UnsupportedOperationException();
	}

	// ── HttpMessage surface ──

	private Header[] allHeaders() {
		List<Header> headers = new ArrayList<>();
		for (int i = 0; i < response.headers().size(); i++) {
			headers.add(new BasicHeader(response.headers().name(i), response.headers().value(i)));
		}
		return headers.toArray(new Header[0]);
	}

	@Override
	public ProtocolVersion getProtocolVersion() {
		return statusLine.getProtocolVersion();
	}

	public void setProtocolVersion(ProtocolVersion v) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean containsHeader(String name) {
		return response.header(name) != null;
	}

	@Override
	public Header[] getHeaders(String name) {
		List<String> values = response.headers().values(name);
		List<Header> headers = new ArrayList<>(values.size());
		for (String value : values) {
			headers.add(new BasicHeader(name, value));
		}
		return headers.toArray(new Header[0]);
	}

	@Override
	public Header getFirstHeader(String name) {
		String value = response.header(name);
		return value == null ? null : new BasicHeader(name, value);
	}

	@Override
	public Header getLastHeader(String name) {
		List<String> values = response.headers().values(name);
		return values.isEmpty() ? null : new BasicHeader(name, values.get(values.size() - 1));
	}

	@Override
	public Header[] getAllHeaders() {
		return allHeaders();
	}

	/**
	 * convenience method (not part of the 4.4.13 HttpMessage interface)
	 */
	public Header getHeader(String name) {
		return getFirstHeader(name);
	}

	@Override
	public void addHeader(Header header) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void addHeader(String name, String value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setHeader(Header header) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setHeader(String name, String value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setHeaders(Header[] headers) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void removeHeader(Header header) {
		throw new UnsupportedOperationException();
	}

	public void removeHeader(String name) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void removeHeaders(String name) {
		throw new UnsupportedOperationException();
	}

	@Override
	public HeaderIterator headerIterator() {
		return new BasicHeaderIterator(allHeaders(), null);
	}

	@Override
	public HeaderIterator headerIterator(String name) {
		return new BasicHeaderIterator(getHeaders(name), name);
	}

	@Override
	public HttpParams getParams() {
		return new BasicHttpParams();
	}

	@Override
	public void setParams(HttpParams params) {
		throw new UnsupportedOperationException();
	}

}
