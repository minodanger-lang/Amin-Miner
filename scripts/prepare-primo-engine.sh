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

extern "C" {
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

# Fail closed: this upstream revision explicitly disables both TLS certificate
# and hostname verification. Do not build/package it until the Android DNS
# bridge preserves the original hostname for SNI and verified TLS.
if grep -Eq 'CURLOPT_SSL_VERIFYPEER,[[:space:]]*0L|CURLOPT_SSL_VERIFYHOST,[[:space:]]*0L' \
  "$ENGINE/src/stratum_transport.cpp"; then
  echo "ERROR: upstream TLS verification is disabled; secure Android hostname/DNS handling must be patched before engine packaging." >&2
  exit 2
fi

echo "Prepared pinned engine with developer-fee scheduler disabled and TLS verification enabled."
