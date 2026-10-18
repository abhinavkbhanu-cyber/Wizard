# RB22 Antivirus for Android

**Status: alpha prototype — not a production antivirus.** This is a separate Android project at `mobile/rb22-antivirus/`; it does not merge with RB22 Game Booster or either Windows app. Minimum supported OS: Android 9 (API 28).

## Included
- Kotlin + Jetpack Compose black/red interface.
- Locally calculates a selected APK's SHA-256 fingerprint (maximum 200 MB).
- Checks the public GitHub release feed on launch and on demand for tags named `rb22-antivirus-v<version>`.
- Opens the official release page for review and manual installation.
- Android 9+ build pipeline and a short-lived CI APK artifact.

## Important limitations
This build **does not detect malware**. A SHA-256 fingerprint is an identifier, not a verdict. No malware signature engine, reputation service, signed threat-feed, real-time/system-wide protection, or installed-app scan is included. Do not rely on it to protect a device.

The updater checks for a newer published version and directs the user to the official release page. It does not silently install APKs. Android installation requires user action, and a real update channel needs a stable, protected release-signing key so updates match the installed app. Never publish a production release until signing is configured and verified.

## Signed alpha release

The tag-triggered release workflow publishes a signed alpha APK only when all four repository Actions secrets are configured: `RB22_ANTIVIRUS_KEYSTORE_BASE64`, `RB22_KEYSTORE_PASSWORD`, `RB22_KEY_ALIAS`, and `RB22_KEY_PASSWORD`. Keep the keystore and passwords private; never commit them.

Create a release keystore locally with Android's `keytool`, convert the keystore file to Base64, and add its contents plus the three passwords/alias as Actions secrets in GitHub repository Settings. Back up the keystore securely: future Android updates must use the same signing key. The workflow intentionally fails rather than publishing an unsigned or randomly signed release if secrets are missing.

Even a signed alpha is **not a malware detector** and must not be used as a security product.

## Build
Open this folder in Android Studio with JDK 17 and Android SDK platform 35 installed. Allow Gradle to sync, then run the `app` configuration on an Android 9+ emulator/device. GitHub Actions builds a debug APK and keeps it as a 14-day CI artifact; this is not a production release.

## Privacy
The selected file is processed locally and is not uploaded. Update checks contact GitHub's public releases API over HTTPS. The app does not request broad installed-package visibility.
