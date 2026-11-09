# Pinned Verus engine integration

The upstream engine is pinned as a Git submodule at:

- Repository: https://github.com/PrimoLab/primo-arm-miner
- Reviewed revision: `a11d481b8614290823b0d59597293fba6f46831c`
- Upstream license: GPL-3.0-only

## Current integration gate

`scripts/prepare-primo-engine.sh` applies the Amin Miner fork policy by replacing the upstream scheduled developer-fee scheduler with API-compatible no-op functions. The patch is intentionally narrow: the upstream Stratum session/failover implementation remains unchanged.

The script then **fails closed** while upstream TLS verification remains disabled. Inspection of the pinned source found that the native transport already has a DNS fallback which injects resolved addresses through libcurl `CURLOPT_RESOLVE`; that mechanism retains the original hostname for SNI and hostname checks. The Android service separately rewrites configured pool URLs to IP literals before launching the native process, so that rewrite must be removed or replaced before verified TLS can work. The native fallback queries public DNS resolvers only after the platform resolver fails; this behavior must remain explicit and documented because it may bypass a user-configured private DNS/VPN resolver on that fallback path.

Enabling `CURLOPT_SSL_VERIFYPEER` and `CURLOPT_SSL_VERIFYHOST` is still not sufficient by itself: the current static curl/mbedTLS build explicitly omits a CA bundle and upstream documentation assumes certificate verification is disabled for self-signed pools. A trusted CA source and a tested policy for pools with self-signed certificates are required. Do not accept invalid certificates, ship TLS with verification disabled, or silently downgrade TLS pools to plaintext.

## Scope

- First target: VerusHash v2.2 only.
- RandomX/Monero is not included in the first phone build.
- No private keys are stored; only a public payout address and pool worker settings are needed.
- The native engine must be built for Android `arm64-v8a` with self-contained dependencies before APK packaging.
- The existing Termux scripts are not treated as a reproducible GitHub Actions NDK build. CI integration is a separate milestone.
- The engine is not yet packaged into the app. The Android app continues to report that mining is not implemented until the secure transport and NDK build gates pass.

## License compliance

Keep the upstream GPL-3.0-only license and notices. Distribute the corresponding source and modifications with any binary release. Do not relicense the combined covered work under a more permissive license.
