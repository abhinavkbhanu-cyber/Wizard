# RB22 Antivirus — Android

Standalone Android security project, separate from RB22 Game Booster.

## Alpha 0.2.0 — RB22 Shield preview
- Opt-in local DNS filtering VPN, requiring Android's VPN approval.
- Captures only DNS packets routed to the app's virtual DNS endpoint.
- Checks hostnames against a small bundled denylist and returns NXDOMAIN for listed hosts.
- Forwards other DNS queries to Cloudflare DNS (1.1.1.1).
- Does not upload or store app inventory, browsing history, or DNS query logs.
- Uses a low-importance foreground notification while active.

## Important limitations
This is a preview, not a complete system-wide ad blocker:
- The starter denylist is small and misses many ads and malicious domains.
- DNS filtering cannot remove ads served from the same host as wanted content.
- DNS-over-HTTPS, DNS-over-TLS, hard-coded IPs, app proxies, and other VPNs can bypass this DNS filter.
- It does not inspect HTTPS page paths, remove webpage pop-up elements, or guarantee every redirect is blocked.
- This preview handles IPv4 DNS only; IPv6 and encrypted-DNS coverage need further work.
- A VPN-based filter cannot run at the same time as another VPN on Android.

Do not claim that this preview blocks all ads or all unsafe redirects.

## Battery and privacy
- No periodic polling or wake lock.
- Only DNS packets sent to the virtual DNS endpoint are processed.
- Upstream DNS socket is protected from the VPN to avoid a routing loop.
- Runs only after explicit user activation.

## Build
JDK 17, Android SDK 35, Gradle 8.9:
```bash
gradle :app:assembleDebug --no-daemon
```

## Next milestones
1. Add automated tests for DNS parsing, NXDOMAIN generation, and domain suffix matching.
2. Add signed blocklist updates and rollback protection.
3. Add allowlist, block counters, and per-category switches.
4. Add IPv6 and encrypted-DNS handling strategy.
5. Test on Android 9–16, VPN conflicts, sleep, and network reconnect.
