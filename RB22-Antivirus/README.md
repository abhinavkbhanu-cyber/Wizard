# RB22 Antivirus — Android

Standalone Android security project, separate from RB22 Game Booster.

## Alpha 0.3.0 — RB22 Shield stronger starter list
- Opt-in local DNS filtering VPN, requiring Android's VPN approval.
- Expanded built-in list covering common ad networks, mobile-game ad SDKs, trackers, and advertising exchanges.
- Exact-domain and subdomain matching; listed domains receive NXDOMAIN.
- Other IPv4 UDP DNS queries are forwarded to Cloudflare DNS (1.1.1.1).
- No browsing history or DNS query logs are stored or uploaded by this filtering module.

## Important limitations
This is still a DNS-level alpha, not a complete ad blocker:
- It may block some third-party ad domains used by mobile games, but cannot guarantee every in-game ad is removed.
- YouTube video ads commonly share delivery infrastructure with wanted video content; DNS filtering cannot reliably separate them.
- DNS filtering cannot remove webpage pop-up elements or ads served from the same host as wanted content.
- DNS-over-HTTPS, DNS-over-TLS, hard-coded IPs, app proxies, and other VPNs can bypass this filter.
- It handles IPv4 UDP DNS only; IPv6 and TCP DNS fallback still need work.
- A VPN-based filter cannot run at the same time as another VPN on Android.
- The list is a manually curated starter list and is not comprehensive; some apps may malfunction if a required domain is blocked.

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
2. Add a safe allowlist, block counters, and per-category switches.
3. Add validated community blocklist updates with rollback protection.
4. Add IPv6 and TCP DNS support with safe packet handling.
5. Test on Android 9–16, VPN conflicts, sleep, and network reconnect.
