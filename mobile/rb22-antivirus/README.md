# RB22 Antivirus for Android

**Status: experimental developer alpha. Not a replacement for Google Play Protect or a production antivirus.** This is a separate Android project at `mobile/rb22-antivirus/`; it does not merge with RB22 Game Booster or either Windows app. Minimum OS: Android 9 (API 28).

## Implemented prototype features
- Local APK scan: SHA-256, Android APK metadata and requested permissions, plus bounded inspection of selected DEX strings for a few risky API indicators.
- Installed-app inventory: reviews accessible package permissions and debuggable status. Inventory mode does not inspect every app's code.
- Install/update event alerts: a manifest receiver queues a WorkManager scan for an app reported as newly installed or replaced; suspicious heuristic findings can trigger a notification.
- Toggle to enable/disable those install/update alerts.
- Unit tests for SHA-256 and several heuristic indicators.
- Checks the public GitHub release feed on launch/on demand; background update notices still require manual review and installation.

## Detection limitations — read before use
This is **not a full antivirus engine**. It has no trusted malware signature/hash feed, no signed threat-intelligence update channel, no cloud reputation service, no behavior monitoring, no system-wide install blocking, and no quarantine/removal. Static indicators are weak signals: legitimate apps can use SMS, accessibility, dynamic-loading or process APIs, and malware can evade these checks. A “No known indicators found” result does not mean an app is safe. Continue using Google Play Protect and install apps only from trusted sources.

Real-time protection here means best-effort install/update event triage and notifications, **not** continuous system-wide protection. Android can delay background work, some events can be missed, and the scan cannot prevent the installer from completing. Notifications require permission on Android 13+. Do not claim parity with commercial antivirus products without a real threat feed and independent testing.

## Package visibility
The installed-app inventory requests `QUERY_ALL_PACKAGES` so it can enumerate installed apps. Google Play treats broad package visibility as restricted and requires a permitted core use case and policy review. Do not submit to Google Play without checking current policy and considering an alternative visibility design.

## Developer-preview release
The tag-triggered workflow can publish a signed prerelease only when all four repository Actions secrets are configured: `RB22_ANTIVIRUS_KEYSTORE_BASE64`, `RB22_KEYSTORE_PASSWORD`, `RB22_KEY_ALIAS`, and `RB22_KEY_PASSWORD`. It runs release unit tests, verifies the APK signature, and attaches a SHA-256 checksum. Never commit the keystore or paste secrets into chat. Future Android updates must use the same signing key.

## Build and test
Open this folder in Android Studio with JDK 17 and Android SDK platform 35 installed:
- `gradle :app:testDebugUnitTest`
- `gradle :app:assembleDebug`

CI debug artifacts are temporary and are not production releases.

## Privacy
Selected APKs are processed locally and are not uploaded. Update checks contact GitHub's public releases API over HTTPS. The broad package visibility permission is used for the explicit installed-app review feature.
