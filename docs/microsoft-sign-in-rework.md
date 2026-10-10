# Microsoft sign-in rework: session notes

Status: **unverified.** The code was not compiled and was not tested against Microsoft's live services.
Written so the next session can check the reasoning, not as proof that sign-in works.

## Problem reported

```
java.lang.RuntimeException: error occur whilst refreshing token: invalid_grant
```

The user has been unable to sign in to Xbox-authenticated servers for a long time.

## Theory

1. **The one-time code and the refresh token share one file.** `src/test/kotlin/dev/sora/relay/Main.kt`
   reads `.ms_refresh_token` and passes its contents to `XboxDeviceInfo.refreshToken`. That function
   decides between an authorization code and a refresh token by checking the length of the last
   dot-separated segment (36 characters means a UUID, so it's a code). Both paths report failure with the
   same generic message, so the error doesn't say which one was rejected.
   - If the first attempt used an authorization code and failed, the dead code stays in the file.
     Every later run then retries the same code and gets `invalid_grant`. This would look like a
     permanent failure even though a fresh sign-in would work.
   - Refresh tokens can also become invalid for real. Microsoft's Q&A says they can be revoked after
     account security events such as a password change, and that a token unused for 90 days expires
     ([Microsoft Q&A, refresh tokens](https://learn.microsoft.com/en-us/answers/questions/1361319/question-about-refresh-tokens),
     [Microsoft Q&A, invalid_grant](https://learn.microsoft.com/en-us/answers/questions/1195703/getting-invalid-grant-error-when-refreshing-token)).
     Some reports also say public-client refresh tokens are bound to the device they were issued on.
2. **The error hides Microsoft's reason.** The old code only logged `error`, not `error_description`.
   So the user never saw why Microsoft rejected the request.
3. **The Nintendo client id is a poor default.** It's the only client in the code that uses
   `canDirectTitleTokenFetch` (a shortcut that skips the SISU flow). The user said they don't need it.
   The Android client (`0000000048183522`) uses the SISU flow that the code already implements, with an
   `ms-xal-…` redirect. **This is a guess, not verified.**

## Changes made

| File | Change |
| --- | --- |
| `src/main/java/dev/sora/relay/session/listener/xbox/MicrosoftSignIn.kt` (new) | `MicrosoftTokenExchange` posts to `https://login.live.com/oauth20_token.srf` with the same client id for both grant types and parses `error` / `error_description`. `MicrosoftSignIn` uses `.ms_refresh_token` first, falls back to a one-time `.ms_auth_code` if the refresh token is rejected, writes back rotated refresh tokens, and deletes the code after a successful exchange. |
| `src/main/java/dev/sora/relay/session/listener/xbox/XboxDeviceInfo.kt` | `refreshToken()` now delegates to `MicrosoftTokenExchange` (same signature, so old callers still compile). Removed `DEVICE_NINTENDO`. |
| `src/test/kotlin/dev/sora/relay/Main.kt` | Uses `MicrosoftSignIn` with `.ms_refresh_token` and `.ms_auth_code`. Default device is `DEVICE_ANDROID`. Starts the Xbox path when either file exists. |
| `src/test/README.md` | New sign-in steps, Android client link, troubleshooting for both `invalid_grant` cases. |
| `.gitignore` | Added `/.ms_auth_code`. |

Nothing else changed. `ModuleDeviceSpoof` and `DeviceOS` still reference Nintendo, which is a separate
device-spoof feature, not sign-in.

## What is unverified

- **Compilation.** Java and Gradle are not installed in the sandbox. Maven Central, `repo.opencollab.dev`,
  and `gradlew` downloads are blocked. The Kotlin changes have not been compiled.
- **Android client against Microsoft.** I haven't confirmed that the Android client id accepts
  `redirect_uri=https://login.live.com/oauth20_desktop.srf` with the `MBI_SSL` scope, or that the SISU
  path in `RelayListenerXboxLogin` works with it.
- **Whether the old refresh token was the real cause.** The likely explanation is the dead-code-in-file
  issue, but the user has not yet confirmed that a fresh code with the new code path succeeds.
- **`Client-Version: 1.19.50`** in `RelayListenerXboxLogin.fetchRawChain`. This is a 2023 value that I
  haven't changed. It may cause a failure after sign-in succeeds.

## Things a future session should check first

1. Run `./gradlew build` with JDK 21 and fix any compile errors in the new Kotlin file.
2. Delete `.ms_refresh_token`, paste a fresh Android-client code into `.ms_auth_code`, and run the program.
   Record the exact output. Check whether `MicrosoftSignIn` reports `invalid_grant` for the code, and what
   Microsoft's `error_description` says.
3. If the Android client fails, the likely next step is the device-code flow that `prismarine-auth` uses
   (`login.microsoftonline.com/consumers/oauth2/v2.0/devicecode`, client `389b1b32-b5d5-43b2-bddc-84ce938d6737`),
   with a refresh endpoint that matches the one that issued the token. Verify against prismarine-auth's
   current source before copying anything.
4. Replace `Client-Version` with a value that matches the target Bedrock version.

## Sources consulted

- prismarine-auth endpoint constants (`src/common/Constants.js`): Live token endpoint
  `https://login.live.com/oauth20_token.srf`, device-code endpoint `https://login.live.com/oauth20_connect.srf`,
  and the Xbox/Minecraft endpoints.
- Microsoft Q&A threads on `invalid_grant` and refresh token lifetime (linked above).
- A PR in another project describing a v2.0 device-code token being redeemed at the wrong endpoint, which
  produced `invalid_grant`. This project doesn't use that flow, so the fix doesn't apply directly.
