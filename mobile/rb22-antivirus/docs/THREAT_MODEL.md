# Initial threat model

## Threats
- Malformed APKs causing excessive resource use
- Substituted or tampered update metadata/downloads
- False-positive malware labels
- Unwanted uploads or telemetry
- Stale threat definitions presented as current

## Mitigations planned
- Bounded streaming reads and parser limits
- Authenticated metadata, SHA-256 verification and certificate identity checks
- Evidence-backed verdicts including an inconclusive state
- Local-first scanning and network-capture tests
- Visible definition timestamps and offline status
- Unique, idempotent WorkManager tasks with bounded retries

## Current limitations
This prototype only fingerprints a user-selected file. It has no malware detection engine, threat feed, real-time protection, or production update service. It must not be presented as an effective antivirus until independently tested.
