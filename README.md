# Amin Miner

A native Android APK project for **transparent, user-controlled CPU mining research**.

## Target test device
- Infinix X6726B
- 6 GB RAM
- Android APK (sideloaded test distribution)

## Initial engineering decision
Do not assume a coin is profitable or choose an engine from marketing claims. The first device benchmark will compare:
1. **VerusHash v2.2 / Verus (VRSC)** — candidate for low-power ARM CPU testing.
2. **RandomX / Monero (XMR)** — comparison baseline; RandomX is memory-intensive and may be unsuitable for a phone.

The final engine/coin choice is gated on measured sustained hashrate, accepted shares, thermal throttling, battery drain, pool compatibility, and current network economics. No simulated hashrate or earnings will be displayed.

## Planned architecture
- Kotlin Android application layer.
- C++ native mining engine behind JNI, added only after upstream licensing and ARM64 compatibility are reviewed.
- Pool connection and wallet-address configuration; no private-key custody.
- Foreground service with a persistent notification and explicit user start/stop.
- Thermal and battery safeguards; conservative initial CPU-thread limit.
- Local session history and pool-reported share statistics.

## Safety and honesty requirements
- Mining never starts automatically or silently.
- Stop mining on thermal/battery safety thresholds and when the user stops it.
- Show measured device telemetry separately from pool-confirmed results.
- No cloud-mining claims, guaranteed returns, or fabricated balances.
- Direct APK distribution only; Google Play policy does not allow on-device cryptocurrency mining apps.

## Licensing

The upstream candidate `PrimoLab/primo-arm-miner` declares **GPL-3.0-only** (not GPL-3.0-or-later). If its code is incorporated, the combined covered work and corresponding source must comply with GPL-3.0, including source availability and preservation of upstream copyright/license notices. Third-party components retain their own licenses; see `THIRD_PARTY_NOTICES.md` when added. This repository does not claim that the engine has been integrated yet.

## Project status
This repository currently contains the Android project foundation. **Mining is not implemented yet**, and no APK has been built or tested on the target phone. The next milestone is a reproducible ARM64 benchmark and licensed engine integration.
