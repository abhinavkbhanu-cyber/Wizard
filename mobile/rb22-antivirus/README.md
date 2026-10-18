# RB22 Antivirus for Android

**Status: starter prototype — not a production antivirus.** This project is intentionally isolated under `mobile/rb22-antivirus/`; do not merge it into the Windows application code. Target: Android 9+ (API 28).

## Current prototype scope
- Kotlin + Jetpack Compose black/red dashboard.
- User-selected APK fingerprinting with SHA-256, performed locally.
- Explicitly labels fingerprinting as *not a malware verdict*.
- Update architecture specification and Android CI workflow.

## Not implemented yet
No malware signature engine, reputation service, signed threat-feed, real-time protection, full installed-app scanning, or production app updater exists yet. Do not describe this scaffold as protecting devices from malware.

## Build
Open this folder in Android Studio with JDK 17 and Android SDK platform 35 installed. Allow Gradle to sync, then run the `app` configuration on an Android 9+ emulator/device. The CI workflow installs Gradle and builds the debug APK.

## Privacy
The selected file is processed locally and is not uploaded. This prototype does not request broad installed-package visibility. See `docs/UPDATE_SYSTEM.md` before implementing release updates.
