# Pinned Verus engine integration

The upstream engine is pinned as a Git submodule at:

- Repository: https://github.com/PrimoLab/primo-arm-miner
- Reviewed revision: `a11d481b8614290823b0d59597293fba6f46831c`
- Upstream license: GPL-3.0-only

## Current integration gate

`scripts/prepare-primo-engine.sh` applies the Amin Miner fork policy by replacing the upstream scheduled developer-fee scheduler with API-compatible no-op functions. The patch is intentionally narrow: the upstream Stratum session/failover implementation remains unchanged.

The preparation script now patches the pinned transport to require `PRIMO_CA_BUNDLE`, enables certificate-chain verification and hostname verification, and refuses TLS connections when a trusted CA bundle is not supplied. The native DNS fallback injects addresses through libcurl `CURLOPT_RESOLVE`, retaining the original hostname for SNI and hostname checks. The Android service must pass the CA bundle path and must not rewrite pool URLs to IP literals. The native fallback queries public DNS resolvers only after platform resolver failure; this may bypass private DNS/VPN resolver policy on that fallback path and remains a documented limitation.

The new `scripts/build-verus-engine-android.sh` cross-builds pinned mbedTLS, Jansson, and libcurl, then builds the pinned Verus engine for `arm64-v8a` with RandomX disabled. It emits a standalone engine artifact and the Mozilla CA bundle. This is an engine-build artifact only: the existing app does not yet contain foreground mining service/UI integration, so the APK is not represented as a working miner. Self-signed pool certificates will be rejected; no insecure exception or plaintext downgrade is allowed.

## Scope

- First target: VerusHash v2.2 only.
- RandomX/Monero is not included in the first phone build.
- No private keys are stored; only a public payout address and pool worker settings are needed.
- The native engine must be built for Android `arm64-v8a` with self-contained dependencies before APK packaging.
- The existing Termux scripts are not treated as a reproducible GitHub Actions NDK build. CI integration is a separate milestone.
- The engine is not yet packaged into the app. The Android app continues to report that mining is not implemented until the secure transport and NDK build gates pass.

## License compliance

Keep the upstream GPL-3.0-only license and notices. Distribute the corresponding source and modifications with any binary release. Do not relicense the combined covered work under a more permissive license.
