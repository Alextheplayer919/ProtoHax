package dev.sora.relay.session.listener.xbox

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import dev.sora.relay.utils.HttpUtils
import dev.sora.relay.utils.logInfo
import dev.sora.relay.utils.logWarn
import okhttp3.FormBody
import okhttp3.Request
import java.io.File

/**
 * Thrown when Microsoft's token endpoint returns an error. [isTokenRejected] is true when the
 * submitted refresh token or authorization code is no longer valid (`invalid_grant`), which
 * means the user has to sign in again instead of retrying.
 */
class MicrosoftAuthException(
	message: String,
	val errorCode: String?,
	val isTokenRejected: Boolean,
) : RuntimeException(message)

/**
 * Talks to the Microsoft Live token endpoint used by the Android and iOS Xbox client ids.
 * Both the one-time authorization code and the refresh token are sent to the same endpoint
 * with the same client id, which is what Microsoft requires for tokens issued by this flow.
 */
object MicrosoftTokenExchange {

	const val TOKEN_ENDPOINT = "https://login.live.com/oauth20_token.srf"
	const val DESKTOP_REDIRECT_URI = "https://login.live.com/oauth20_desktop.srf"
	const val XBOX_SCOPE = "service::user.auth.xboxlive.com::MBI_SSL"

	/** Exchanges a one-time authorization code for an (access token, refresh token) pair. */
	fun exchangeAuthCode(deviceInfo: XboxDeviceInfo, authCode: String): Pair<String, String> {
		val form = FormBody.Builder()
			.add("client_id", deviceInfo.appId)
			.add("redirect_uri", DESKTOP_REDIRECT_URI)
			.add("grant_type", "authorization_code")
			.add("code", authCode.trim())
			.build()
		return post(form, "authorization code")
	}

	/** Uses a refresh token to obtain a new (access token, refresh token) pair. */
	fun refresh(deviceInfo: XboxDeviceInfo, refreshToken: String): Pair<String, String> {
		val form = FormBody.Builder()
			.add("client_id", deviceInfo.appId)
			.add("redirect_uri", DESKTOP_REDIRECT_URI)
			.add("scope", XBOX_SCOPE)
			.add("grant_type", "refresh_token")
			.add("refresh_token", refreshToken.trim())
			.build()
		return post(form, "refresh token")
	}

	private fun post(form: FormBody, what: String): Pair<String, String> {
		val request = Request.Builder()
			.url(TOKEN_ENDPOINT)
			.header("Content-Type", "application/x-www-form-urlencoded")
			.post(form)
			.build()

		HttpUtils.client.newCall(request).execute().use { response ->
			val raw = response.body?.string().orEmpty()
			val body = try {
				JsonParser.parseString(raw).asJsonObject
			} catch (e: Exception) {
				throw MicrosoftAuthException(
					"Microsoft returned a non-JSON response for the $what (HTTP ${response.code})",
					null, false
				)
			}

			val accessToken = body.stringOrNull("access_token")
			val refreshToken = body.stringOrNull("refresh_token")
			if (response.isSuccessful && accessToken != null && refreshToken != null) {
				return accessToken to refreshToken
			}

			val error = body.stringOrNull("error")
			val description = body.stringOrNull("error_description")?.lineSequence()?.firstOrNull()?.trim()
			throw MicrosoftAuthException(
				buildString {
					append("Microsoft rejected the $what (HTTP ${response.code})")
					if (error != null) append(": ").append(error)
					if (!description.isNullOrEmpty()) append(" - ").append(description)
				},
				error,
				error == "invalid_grant"
			)
		}
	}

	private fun JsonObject.stringOrNull(key: String): String? {
		val element = get(key) ?: return null
		return if (element.isJsonPrimitive) element.asString else null
	}
}

/**
 * Decides which Microsoft credential to use and keeps the stored refresh token up to date.
 *
 * Order of use:
 *  1. the refresh token file, if present;
 *  2. the authorization code file, if there is no usable refresh token (it is one-time and
 *     expires quickly, so a fresh code has to be pasted in after a rejection).
 *
 * After a successful sign-in the rotated refresh token is written back to the refresh token
 * file and the authorization code file is removed.
 */
class MicrosoftSignIn(
	private val deviceInfo: XboxDeviceInfo,
	private val refreshTokenFile: File,
	private val authCodeFile: File,
) {

	fun acquireAccessToken(): String {
		val storedRefresh = refreshTokenFile.takeIf { it.isFile }?.readText()?.trim().orEmpty()
		var refreshFailure: MicrosoftAuthException? = null

		if (storedRefresh.isNotEmpty()) {
			try {
				val (access, refresh) = MicrosoftTokenExchange.refresh(deviceInfo, storedRefresh)
				refreshTokenFile.writeText(refresh)
				return access
			} catch (e: MicrosoftAuthException) {
				if (!e.isTokenRejected) throw e
				logWarn("stored Microsoft refresh token was rejected: ${e.message}")
				refreshFailure = e
			}
		}

		val code = authCodeFile.takeIf { it.isFile }?.readText()?.trim().orEmpty()
		if (code.isNotEmpty()) {
			try {
				val (access, refresh) = MicrosoftTokenExchange.exchangeAuthCode(deviceInfo, code)
				refreshTokenFile.writeText(refresh)
				authCodeFile.delete()
				logInfo("signed in with Microsoft authorization code")
				return access
			} catch (e: MicrosoftAuthException) {
				if (!e.isTokenRejected) throw e
				throw MicrosoftAuthException(
					"${e.message}. The authorization code in ${authCodeFile.name} is expired or was already used. " +
						"Sign in again and paste the new code into ${authCodeFile.name}.",
					e.errorCode, true
				)
			}
		}

		if (refreshFailure != null) {
			throw MicrosoftAuthException(
				"${refreshFailure.message}. Delete ${refreshTokenFile.name} and paste a new authorization code " +
					"into ${authCodeFile.name}.",
				refreshFailure.errorCode, true
			)
		}
		throw MicrosoftAuthException(
			"No Microsoft credentials found. Put an authorization code into ${authCodeFile.name} " +
				"or run in offline mode.",
			null, false
		)
	}
}
