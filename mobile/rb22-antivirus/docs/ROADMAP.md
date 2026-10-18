# RB22 Antivirus — Protection Roadmap

## Goal

Build a privacy-first Android security app that can be evaluated fairly against established products. Do not claim it beats Malwarebytes unless independent, repeatable testing supports that claim.

## Current status (alpha prototype)

- Calculates SHA-256 for a user-selected file, with a bounded 200 MiB read.
- Checks the official GitHub release feed and points users to releases.
- Does not detect malware, remove threats, provide real-time protection, or scan all installed apps.
- A file hash is an identifier, not a safety verdict.

## Build plan

### Phase 1 — Reliable foundation

- [ ] Keep the scanner isolated from UI code and run file work off the main thread.
- [ ] Add unit tests for hashes, file-size limits, malformed inputs, version ordering, and error paths.
- [ ] Make every result explicit: Clean by available checks, Suspicious, Malicious (matched evidence), or Inconclusive. Never label an unchecked file safe.
- [ ] Add clear offline, stale-data, and update-failure states.
- [ ] Publish signed releases with a documented signing-key recovery/rotation process and verified SHA-256 checksums.

### Phase 2 — Real file inspection

- [ ] Safely parse APK metadata with strict size/time/resource limits.
- [ ] Show package name, version, signer certificate fingerprint, requested permissions, and signing consistency.
- [ ] Implement a versioned, signed threat-definition format containing known-bad hashes and indicators with source, timestamp, and expiry.
- [ ] Verify threat-definition signatures before use; reject expired, malformed, replayed, or unsigned definitions.
- [ ] Add conservative heuristics for suspicious combinations of metadata. Explain evidence and use Inconclusive where evidence is insufficient.
- [ ] Add a test corpus of benign APKs and harmless test fixtures. Do not use real malware on personal devices.

### Phase 3 — User-facing protection

- [ ] Add an installed-app audit using Android APIs and permissions the OS actually allows.
- [ ] Add a manual scan history, scan details, and exportable privacy-safe reports.
- [ ] Add scheduled scans with Android WorkManager, battery/network constraints, and visible last-scan time.
- [ ] Offer user-controlled handling of files they selected. Never silently delete, disable, or upload files.
- [ ] Add phishing protection only after a maintained, signed URL reputation feed and privacy review exist.
- [ ] Make limitations clear: Android app sandboxing prevents a normal app from acting as a universal system-wide antivirus or guaranteeing interception of every app action.

### Phase 4 — Security and performance validation

- [ ] Unit, integration, regression, and malformed-file tests in CI.
- [ ] Test false positives against a representative benign-app set and detection against a controlled, harmless test set.
- [ ] Verify update authenticity, rollback resistance, offline behavior, network failure, and key rotation.
- [ ] Measure battery, memory, scan speed, and accessibility on supported Android versions.
- [ ] Arrange independent security review and publish methodology and measured results before making comparative claims.

## Release gates

Do not call a build production-ready until the scanner and signed definitions are implemented, automated tests pass, update authenticity is verified, privacy behavior is documented, and the known limitations are visible in the app. A successful APK build alone is not evidence of malware protection.

## Privacy principles

- Local-first analysis by default.
- No automatic upload of APKs, file contents, or personal reports.
- If reputation lookups are added, disclose exactly what leaves the device and require an explicit choice.
- Request the minimum Android permissions needed.
- Never request accessibility, VPN, device-admin, or broad storage privileges merely to make the feature list look stronger.
