# RB22 Android v1.0 Beta

This is the Android-only beta line. The Windows project and its release files are not changed.

## Beta scope

- Existing game library, manual booster controls, overlay/HUD, and saved game profiles.
- App Focus: detects the foreground app (with the user's Usage Access permission) and reports available traffic counters. Some Android builds expose only device-wide counters.
- DNS provider selection and the experimental DNS VPN service.
- Device/RAM/thermal information where Android exposes it.
- WorkManager-based update checks.

## Important limitations

- App Focus is a monitor, not a bandwidth accelerator or traffic-shaping engine.
- Android does not provide ordinary apps with a supported way to force every other app to consume the full internet connection, reserve all bandwidth, or raise an app's ISP speed.
- A speed-test-style traffic generator is intentionally not included.
- FPS cannot be increased universally by a third-party app; actual game FPS is controlled by the game, device, thermal state, and Android.
- DNS changes can affect name resolution, not raw download bandwidth or ping to every game server.
- The custom DNS VPN is experimental. Test on Wi-Fi and mobile data before relying on it; stop it if connectivity is affected.
- Security integration is not yet an antivirus engine. This beta does not claim malware detection or real-time protection.

## Build

The GitHub Actions workflow `.github/workflows/android-beta.yml` builds the debug APK on pushes to this branch and on manual dispatch. The APK is uploaded as a workflow artifact. A successful build is not the same as device testing or a signed public release.

## Device test checklist

- Install/launch on Android 7.0+ (minSdk 24).
- Check all dashboard controls and app navigation.
- Test overlay permission flow and dismiss/stop controls.
- Test App Focus with Usage Access granted and denied.
- Verify whether counters are per-app or device-wide on each device.
- Test DNS service on Wi-Fi and mobile data; verify connectivity after stopping.
- Check battery use and background behavior.
- Confirm update check handles unavailable network and missing release assets gracefully.

## Release status

Pre-release development branch. Not a production-ready or Play Store-certified build.
