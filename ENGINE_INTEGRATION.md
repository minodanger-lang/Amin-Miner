# Pinned Verus engine integration

The upstream engine is pinned as a Git submodule at:

- Repository: https://github.com/PrimoLab/primo-arm-miner
- Reviewed revision: `a11d481b8614290823b0d59597293fba6f46831c`
- Upstream license: GPL-3.0-only

## Current integration gate

`scripts/prepare-primo-engine.sh` applies the Amin Miner fork policy by replacing the upstream scheduled developer-fee scheduler with API-compatible no-op functions. The patch is intentionally narrow: the upstream Stratum session/failover implementation remains unchanged.

The script then **fails closed** while upstream TLS verification remains disabled. In this pinned revision, the Android wrapper resolves pool hostnames to IP literals before launching the native process, while the native TLS transport disables both peer and hostname verification. Simply enabling hostname verification would break those IP-literal URLs and would not preserve SNI. The next required engineering change is an Android-to-native DNS mapping that uses libcurl's `CURLOPT_RESOLVE` while retaining the original hostname in the URL; only then can TLS peer and hostname verification be safely enabled.

Do not remove this gate, ship TLS with verification disabled, or silently downgrade TLS pools to plaintext.

## Scope

- First target: VerusHash v2.2 only.
- RandomX/Monero is not included in the first phone build.
- No private keys are stored; only a public payout address and pool worker settings are needed.
- The native engine must be built for Android `arm64-v8a` with self-contained dependencies before APK packaging.
- The existing Termux scripts are not treated as a reproducible GitHub Actions NDK build. CI integration is a separate milestone.
- The engine is not yet packaged into the app. The Android app continues to report that mining is not implemented until the secure transport and NDK build gates pass.

## License compliance

Keep the upstream GPL-3.0-only license and notices. Distribute the corresponding source and modifications with any binary release. Do not relicense the combined covered work under a more permissive license.
