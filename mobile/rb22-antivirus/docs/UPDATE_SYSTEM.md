# RB22 Mobile Update System (design specification)

This document describes a shared update protocol for Android mobile apps only. Windows RB22 PC releases remain separate.

## Distribution strategy
- Google Play builds should use Play's supported in-app update mechanism.
- Direct APK builds should download authenticated release metadata over HTTPS, verify the metadata signature, enforce package ID/version/channel policy, verify the APK SHA-256 and signing-certificate identity, and then hand off to Android's supported package installer. Android may require user confirmation; silent arbitrary APK installation is not promised.
- Antivirus definitions are a separate signed data update and must not require an app reinstall.

## Required signed manifest fields
Schema version; package ID; version code/name; minimum SDK; release channel; release notes; HTTPS APK URL; expected byte size and SHA-256; signing-key identity; issue/expiry time; signature over canonical manifest bytes.

## Fail-closed checks
Reject invalid signatures, wrong package IDs, hash mismatches, expired metadata, unauthorized downgrades, unsupported SDK requirements, oversized downloads, and unexpected signing identities. Never execute downloaded scripts. Do not put private signing keys or API secrets in source control or the APK.

## Background and privacy
Use unique WorkManager jobs with network constraints, bounded retries/backoff, cancellation and resumable downloads. Android controls actual job timing; periodic jobs are not exact alarms. Do not upload APKs, contacts, identifiers, or scan reports as part of update checks. Keep the currently installed app if update validation fails.

## Rollout
1. Implement and test the updater in this standalone mobile project.
2. Add tests for offline mode, tampering, wrong package, hash mismatch, expiry, interrupted downloads, and signing-identity mismatch.
3. Integrate the verified shared protocol into each discovered Android mobile project.
4. Audit PC repositories separately for a Windows-appropriate update mechanism; do not share Android installer code with Windows.

**Current state (0.2.0-alpha):** the app checks the public GitHub release feed on launch and schedules a network-constrained WorkManager check approximately every 24 hours. If a newer tagged release is found, it posts a notification when Android notification permission is granted. This is a release notice only: it does not download APKs, verify a signed update manifest, verify APK signing identity, or install updates. The notification opens the official release page. WorkManager timing is best-effort, not exact. Silent installation is not supported for ordinary Android apps.
