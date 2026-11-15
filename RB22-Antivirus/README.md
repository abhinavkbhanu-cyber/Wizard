# RB22 Antivirus — Android

RB22 Antivirus is a standalone Android security project, separate from the existing RB22 Mobile/Game Booster application.

## Product direction
- Complete Android security application
- Android 9 (API 28) and newer
- Protection-first design with low battery use
- Free and open-source development where practical

## Alpha 0.1.0 — first milestone
- Inventories installed applications using Android package visibility.
- Calculates SHA-256 fingerprints for readable APK files.
- Compares fingerprints with a bundled local indicator database.
- Reports unreadable APKs separately; unreadable does not mean clean.
- Runs only after the user starts a scan. It does not continuously poll or hold a background service.

## Important limitations
The bundled threat database currently contains **zero indicators**. This is a scanner foundation and cannot currently identify real malware by signature. Zero matches is not proof that a device is safe.

This alpha does not provide real-time protection, phishing protection, quarantine, cloud reputation, or automatic updates. These features must be implemented and tested before being advertised as available.

## Battery and privacy approach
- No always-running polling loop or wake lock.
- No device-wide VPN or accessibility service.
- APK contents are fingerprinted locally; app inventory and hashes are not uploaded.
- Scanning is user-initiated and can be stopped by closing the app.

## Build
With JDK 17, Android SDK 35, and Gradle 8.9:
```bash
gradle :app:assembleDebug --no-daemon
```
The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Next milestones
1. Add a verified, signed threat-indicator update format.
2. Add safe install-event checks where Android permits, without permanent polling.
3. Add a user-reviewed quarantine/export workflow with reversible actions.
4. Add phishing URL checks with privacy-preserving network requests.
5. Test on emulators and physical devices across Android 9–16.
