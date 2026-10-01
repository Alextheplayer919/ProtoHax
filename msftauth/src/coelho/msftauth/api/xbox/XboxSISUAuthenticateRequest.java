package coelho.msftauth.api.xbox;

import coelho.msftauth.api.APIEncoding;
import coelho.msftauth.api.APIRequest;
import com.google.gson.annotations.SerializedName;
import org.apache.http.HttpRequest;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class XboxSISUAuthenticateRequest extends APIRequest<XboxSISUAuthenticate> {

	public static class Query {
		@SerializedName("code_challenge")
		private String codeChallenge;
		@SerializedName("code_challenge_method")
		private String codeChallengeMethod;
		@SerializedName("state")
		public String state;

		// gson
		public Query() {
		}

		/**
		 * PKCE query: S256 code challenge + public state. The state is exposed
		 * because the XAL redirect URL built after a phone-verification 403
		 * must carry it back.
		 *
		 * @param client client kind marker for the XAL flow (e.g. "phone")
		 */
		public Query(String client) {
			byte[] verifier = new byte[32];
			new SecureRandom().nextBytes(verifier);
			String codeVerifier = Base64.getUrlEncoder().withoutPadding().encodeToString(verifier);
			try {
				MessageDigest digest = MessageDigest.getInstance("SHA-256");
				digest.update(codeVerifier.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
				this.codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest());
			} catch (NoSuchAlgorithmException e) {
				throw new IllegalStateException(e);
			}
			this.codeChallengeMethod = "S256";
			this.state = UUID.randomUUID().toString();
		}

		public String getCodeChallenge() {
			return codeChallenge;
		}

		public String getCodeChallengeMethod() {
			return codeChallengeMethod;
		}
	}

	@SerializedName("AppId")
	private String appId;
	@SerializedName("DeviceToken")
	private String deviceToken;
	private transient XboxDeviceKey deviceKey;
	@SerializedName("Offers")
	private List<String> offers;
	@SerializedName("Query")
	private Query query = new Query();
	@SerializedName("RedirectUri")
	private String redirectURI;
	@SerializedName("Sandbox")
	private String sandbox;
	@SerializedName("TokenType")
	private String tokenType;

	public XboxSISUAuthenticateRequest(String appId, XboxDevice device, String offer, Query query, String redirectURI, String sandbox) {
		this.appId = appId;
		this.deviceKey = device.getKey();
		this.deviceToken = device.getToken().getToken();
		this.offers = Collections.singletonList(offer);
		this.query = query;
		this.redirectURI = redirectURI;
		this.sandbox = sandbox;
		this.tokenType = "code";
	}

	@Override
	public void applyHeader(HttpRequest request) {
		request.setHeader("x-xbl-contract-version", "1");
		this.deviceKey.sign(request);
	}

	@Override
	public String getHttpURL() {
		return "https://sisu.xboxlive.com/authenticate";
	}

	@Override
	public APIEncoding getRequestEncoding() {
		return APIEncoding.JSON;
	}

	@Override
	public APIEncoding getResponseEncoding() {
		return APIEncoding.JSON;
	}

	@Override
	public Class<XboxSISUAuthenticate> getResponseClass() {
		return XboxSISUAuthenticate.class;
	}

}
