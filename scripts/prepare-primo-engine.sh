#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ENGINE="$ROOT/vendor/primo-arm-miner"
EXPECTED="a11d481b8614290823b0d59597293fba6f46831c"

if [[ ! -f "$ENGINE/CMakeLists.txt" ]]; then
  echo "ERROR: Primo ARM Miner submodule is missing. Clone with --recurse-submodules." >&2
  exit 1
fi

ACTUAL="$(git -C "$ENGINE" rev-parse HEAD)"
if [[ "$ACTUAL" != "$EXPECTED" ]]; then
  echo "ERROR: unreviewed engine revision: $ACTUAL (expected $EXPECTED)" >&2
  exit 1
fi

# Replace the upstream scheduled developer-fee scheduler with API-compatible
# no-op functions. This leaves the upstream Stratum failover code intact while
# ensuring no hidden pool can ever be installed or selected.
cat > "$ENGINE/src/dev_fee.cpp" <<'EOF'
/*
 * Amin Miner fork policy: developer-fee mining is disabled.
 * The upstream interface is retained so the reviewed Stratum session code
 * remains source-compatible, but no developer pool is installed or selected.
 * Upstream GPL-3.0-only notices remain in the repository.
 */
#include "dev_fee.h"
#include <stddef.h>

extern "C" {
const struct dev_fee_target *dev_fee_target_for_algo(algo_t) { return NULL; }
int devfee_install_pool(void) { return -1; }
void devfee_runtime_begin(void) {}
bool devfee_is_dev_pool(int) { return false; }
int devfee_seconds_until_transition(void) { return -1; }
bool devfee_transition_due(void) { return false; }
int devfee_take_transition(int) { return -1; }
bool devfee_advance_target(void) { return false; }
int devfee_abort_slice(void) { return -1; }
}
EOF

# Patch the pinned transport to require a trusted CA bundle and verify both
# the certificate chain and the original hostname. Never silently downgrade.
python3 - <<'PY'
from pathlib import Path
p = Path("vendor/primo-arm-miner/src/stratum_transport.cpp")
s = p.read_text()
old = """    if (sctx->use_tls) {
        /* Pool TLS certificates are almost universally self-signed, and the
         * ecosystem norm (xmrig included) is not to verify them — TLS here
         * protects against passive snooping of wallet/worker credentials,
         * not active MITM. Verification also breaks the Android/JVM-resolved
         * connect-by-IP flow (no SNI/hostname to match). */
        curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0L);
        curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 0L);
    }
"""
new = """    if (sctx->use_tls) {
        const char *ca_bundle = getenv("PRIMO_CA_BUNDLE");
        if (!ca_bundle || !ca_bundle[0]) {
            applog(LOG_ERR,
                   "TLS pool refused: PRIMO_CA_BUNDLE must point to a trusted CA bundle");
            stratum_close_transport(sctx);
            return false;
        }
        if (curl_easy_setopt(curl, CURLOPT_CAINFO, ca_bundle) != CURLE_OK ||
            curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 1L) != CURLE_OK ||
            curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 2L) != CURLE_OK) {
            applog(LOG_ERR, "TLS pool refused: could not enable certificate and hostname verification");
            stratum_close_transport(sctx);
            return false;
        }
    }
"""
if old not in s:
    raise SystemExit("TLS block did not match pinned upstream source; refusing to patch")
p.write_text(s.replace(old, new, 1))
PY

if grep -Eq 'CURLOPT_SSL_VERIFYPEER,[[:space:]]*0L|CURLOPT_SSL_VERIFYHOST,[[:space:]]*0L' \
  "$ENGINE/src/stratum_transport.cpp"; then
  echo "ERROR: insecure TLS verification remains in the prepared source." >&2
  exit 2
fi

grep -q 'PRIMO_CA_BUNDLE' "$ENGINE/src/stratum_transport.cpp" || {
  echo "ERROR: trusted CA bundle enforcement was not applied." >&2
  exit 2
}

echo "Prepared pinned engine: dev-fee disabled; TLS requires trusted CA bundle and verifies hostname."
