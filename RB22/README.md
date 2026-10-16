# RB22 PC — v0.3.0 Performance Suite

RB22 PC is a standalone Windows WPF application and is maintained separately from **RB22 Game Booster Pro / RB22 Mobile**. This release focuses on truthful telemetry and reversible controls rather than claiming unsupported FPS gains.

## Performance suite
- Live system CPU utilization using Windows system time counters.
- Physical RAM used/available and memory-load readings from Windows.
- Best-effort detection of selected common running games.
- Running-process picker with an explicit confirmation before setting a selected process to **Above Normal** priority.
- Original process priorities are tracked and can be restored manually or on app close where Windows permits.
- System snapshot report export to the user's Documents folder.
- Hardware overview with OS architecture and logical processor count.
- Rule-based system analysis that makes suggestions without silently terminating apps.
- Startup diagnostics and a `--smoke-test` mode for automated Windows CI.

## Honest limitations
- The profile buttons are application profiles, not CPU overclocking or BIOS changes.
- DNS choices are labels only in this release; they do **not** change Windows adapter DNS settings.
- Angle Snap is a profile label only. It does not manipulate mouse aim.
- FPS, frametime, 1% lows, GPU temperature, clocks and VRAM are not fabricated. A future update needs a supported telemetry provider such as PresentMon or vendor APIs.
- The memory page reports Windows memory pressure and opens Windows Storage settings; it does not claim forced garbage collection frees system RAM.
- Process priority changes are optional, confirmed by the user, limited to Above Normal, and reversible where permissions and process lifetime allow.

## Build and verify
GitHub Actions builds a self-contained Windows x64 executable and launches it with `--smoke-test`. Pull requests run the build and smoke test without publishing a release. The smoke test is the first gate; it does not replace testing on real user hardware.

## Separation
Only files under `RB22/` and the RB22 PC workflow are changed by the PC performance-suite work. The Android/mobile source is not edited.
