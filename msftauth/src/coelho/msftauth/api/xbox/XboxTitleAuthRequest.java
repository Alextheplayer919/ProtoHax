package coelho.msftauth.api.xbox;

import coelho.msftauth.api.APIEncoding;
import coelho.msftauth.api.APIRequest;
import com.google.gson.annotations.SerializedName;
import org.apache.http.HttpRequest;

public class XboxTitleAuthRequest extends APIRequest<XboxToken> {

	private static class Properties {
		@SerializedName("SandboxId")
		public String sandboxId;
		@SerializedName("UserAuth")
		public String userAuth;
		@SerializedName("DeviceToken")
		public String deviceToken;
	}

	@SerializedName("RelyingParty")
	private String relyingParty;
	@SerializedName("TokenType")
	private String tokenType;
	@SerializedName("Properties")
	private Properties properties = new Properties();
	private transient XboxDeviceKey deviceKey;

	/**
	 * direct title token fetch (title.auth.xboxlive.com) — used when the
	 * server does not require the SISU/XAL flow
	 */
	public XboxTitleAuthRequest(String relyingParty, String tokenType, String sandboxId, String siteName, String userAuth, String deviceToken, XboxDeviceKey key) {
		this.relyingParty = relyingParty;
		this.tokenType = tokenType;
		this.properties.sandboxId = sandboxId;
		this.properties.userAuth = userAuth;
		this.properties.deviceToken = deviceToken;
		this.deviceKey = key;
	}

	@Override
	public void applyHeader(HttpRequest request) {
		request.setHeader("x-xbl-contract-version", "1");
		this.deviceKey.sign(request);
	}

	@Override
	public String getHttpURL() {
		return "https://title.auth.xboxlive.com/title/authenticate";
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
	public Class<XboxToken> getResponseClass() {
		return XboxToken.class;
	}

}
