package dev.sora.relay.session.listener.xbox

data class XboxDeviceInfo(val appId: String, val deviceType: String,
						  val allowDirectTitleTokenFetch: Boolean = false,
						  val xalRedirect: String = "") {

	/**
	 * Accepts either a one-time authorization code or a refresh token.
	 * Prefer [MicrosoftSignIn], which keeps the two cases separate and reports clear errors.
	 *
	 * @param token refresh token or authorization code
	 * @return Pair<AccessToken, RefreshToken>
	 */
	fun refreshToken(token: String): Pair<String, String> {
		return if (looksLikeAuthCode(token)) {
			MicrosoftTokenExchange.exchangeAuthCode(this, token)
		} else {
			MicrosoftTokenExchange.refresh(this, token)
		}
	}

	private fun looksLikeAuthCode(token: String): Boolean {
		// authorization codes end with a UUID, refresh tokens do not
		val last = token.split("\n")[0].substring(token.lastIndexOf('.') + 1)
		return last.length == 36
	}

	companion object {

		val DEVICE_ANDROID = XboxDeviceInfo("0000000048183522", "Android", false, xalRedirect = "ms-xal-0000000048183522://auth")
		val DEVICE_IOS = XboxDeviceInfo("000000004c17c01a", "iOS", false, xalRedirect = "ms-xal-000000004c17c01a://auth")
		val DEVICE_NINTENDO = XboxDeviceInfo("00000000441cc96b", "Nintendo", true)
		val devices = arrayOf(DEVICE_ANDROID, DEVICE_IOS, DEVICE_NINTENDO).associateBy { it.deviceType }
	}
}
