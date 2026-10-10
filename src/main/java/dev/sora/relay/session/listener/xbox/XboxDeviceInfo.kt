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
		if (looksLikeAuthCode(token)) {
			return MicrosoftTokenExchange.exchangeAuthCode(this, token)
		}
		return try {
			MicrosoftTokenExchange.refresh(this, token)
		} catch (refreshError: MicrosoftAuthException) {
			if (!refreshError.isTokenRejected) throw refreshError
			// Current one-time codes (`M.R3_BAY...`) do not end in a UUID, so they can't be told
			// apart from refresh tokens by their shape. Microsoft rejects a code sent as a refresh
			// token with invalid_grant, so retry it as a code before giving up.
			try {
				MicrosoftTokenExchange.exchangeAuthCode(this, token)
			} catch (codeError: MicrosoftAuthException) {
				throw MicrosoftAuthException(
					"${refreshError.message}; as an authorization code: ${codeError.message}",
					codeError.errorCode, codeError.isTokenRejected
				)
			}
		}
	}

	private fun looksLikeAuthCode(token: String): Boolean {
		// legacy authorization codes end with a UUID; refresh tokens do not
		val last = token.split("\n")[0].substring(token.lastIndexOf('.') + 1)
		return last.length == 36
	}

	companion object {

		val DEVICE_ANDROID = XboxDeviceInfo("0000000048183522", "Android", false, xalRedirect = "ms-xal-0000000048183522://auth")
		val DEVICE_IOS = XboxDeviceInfo("000000004c17c01a", "iOS", false, xalRedirect = "ms-xal-000000004c17c01a://auth")
		val devices = arrayOf(DEVICE_ANDROID, DEVICE_IOS).associateBy { it.deviceType }
	}
}
