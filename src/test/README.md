# Testing

## Xbox-authenticated servers
You need a Microsoft account signed in through the Android Xbox client id (`0000000048183522`), which is the default in `Main.kt`.
To use the iOS client instead, change `val deviceInfo = XboxDeviceInfo.DEVICE_ANDROID` to `XboxDeviceInfo.DEVICE_IOS` in `Main.kt`.

1. Open this link in your browser and sign in with your Microsoft account:
   https://login.live.com/oauth20_authorize.srf?client_id=0000000048183522&redirect_uri=https://login.live.com/oauth20_desktop.srf&response_type=code&scope=service::user.auth.xboxlive.com::MBI_SSL
2. After signing in, the browser lands on `https://login.live.com/oauth20_desktop.srf?code=M.R3_...`. Copy the value of `code`.
3. Save the code into a file named `.ms_auth_code` in the working directory and run the program.
   The code is one-time and expires within minutes, so use it right away.
4. On success the program writes the refresh token to `.ms_refresh_token`, deletes `.ms_auth_code`, and reuses the refresh token on later runs.

### Troubleshooting
- `invalid_grant` for `.ms_auth_code`: the code is expired or was already used. Get a new code (step 1–2) and replace the file contents.
- `invalid_grant` for `.ms_refresh_token`: the stored refresh token was revoked or expired (for example after a password change). Delete `.ms_refresh_token` and sign in again with a new code in `.ms_auth_code`.

Both files hold credentials. They are listed in `.gitignore`; do not commit them.
