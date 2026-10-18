# RB22 Antivirus for Android

**Status: developer alpha prototype — not a production antivirus.** This is a separate Android project at `mobile/rb22-antivirus/`; it does not merge with RB22 Game Booster or either Windows app. Minimum supported OS: Android 9 (API 28).

## Included
- Kotlin + Jetpack Compose black/red interface.
- Locally calculates a selected file's SHA-256 fingerprint, with a 200 MiB processing limit.
- Unit tests for known SHA-256 output, exact/over-limit inputs, and invalid limits.
- Checks the public GitHub release feed on launch and on demand for tags named `rb22-antivirus-v<version>`.
- Background update checks can notify about a newer release; the user reviews and installs manually.
- Android 9+ CI builds a debug APK only after JVM unit tests pass.

## Important limitations
This build **does not detect malware**. A SHA-256 fingerprint is an identifier, not a verdict. No malware signature engine, signed threat feed, reputation service, real-time/system-wide protection, or installed-app scan is included. Do not rely on it to protect a device.

The updater checks for a newer published version and directs the user to the official release page. It does not silently install APKs. Android installation requires user action, and a real update channel needs a stable, protected release-signing key so updates match the installed app. Never publish a production release until signing is configured and verified.

## Developer-preview release
The tag-triggered workflow can publish a **signed prerelease developer preview** only when all four repository Actions secrets are configured: `RB22_ANTIVIRUS_KEYSTORE_BASE64`, `RB22_KEYSTORE_PASSWORD`, `RB22_KEY_ALIAS`, and `RB22_KEY_PASSWORD`. It runs release unit tests, verifies the APK signature, and attaches a SHA-256 checksum. The workflow intentionally fails rather than publishing an unsigned or randomly signed APK if secrets are missing.

Keep the keystore and passwords private; never commit them or paste them into chat. Back up the keystore securely: future Android updates must use the same signing key. The signing key can be created locally with Android's `keytool`, then the keystore can be Base64-encoded and added through GitHub repository Settings → Secrets and variables → Actions.

Even a signed developer preview is **not a malware detector** and must not be used as a security product.

## Build and test
Open this folder in Android Studio with JDK 17 and Android SDK platform 35 installed. Allow Gradle to sync, then run:
- `gradle :app:testDebugUnitTest`
- `gradle :app:assembleDebug`

The CI debug artifact is temporary (14-day retention), unsigned/debug-signed, and uses a debug-specific application ID. It is not a production release.

## Privacy
Selected file contents are processed locally and are not uploaded. Update checks contact GitHub's public releases API over HTTPS. The app does not request broad installed-package visibility.
