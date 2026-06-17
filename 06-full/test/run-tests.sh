#!/bin/sh
# ─────────────────────────────────────────────────────────────
# PoC 5: Full End-to-End Test (Agent Side)
#
# Combines:
#   - HTTPS with self-signed cert (CA installed in trust store)
#   - CONNECT proxy that rewrites port 443 -> 8443
#   - nginx gating on port 8443 (only trusted repos)
#   - Agent wrappers (npm-safe forces scoped registry)
#   - Permission denylist configs (opencode + Claude Code)
#
# The agent-tester container has:
#   - HTTPS_PROXY set (forces traffic through CONNECT proxy)
#   - Wrappers in PATH
#   - NODE_EXTRA_CA_CERTS for cert validation
#
# ─────────────────────────────────────────────────────────────

PASS=0
FAIL=0
AUTH="admin:admin123"
CA="/etc/ssl/certs/poc-ca.pem"

check() {
    desc="$1"; expected="$2"; actual="$3"
    if [ "$actual" = "$expected" ]; then
        echo "  PASS: $desc"
        PASS=$((PASS + 1))
    else
        echo "  FAIL: $desc - expected $expected, got $actual"
        FAIL=$((FAIL + 1))
    fi
}

echo ""
echo "======================================================"
echo "  PoC 5: Full End-to-End (Agent Perspective)"
echo "======================================================"
echo ""

# ─── Layer 1: HTTPS through CONNECT proxy ───────────────────
echo "--- Layer 1: HTTPS via CONNECT proxy (port rewrite) ───"
echo "    HTTPS_PROXY forces all HTTPS through the proxy."
echo "    Proxy rewrites 443 -> 8443 (scoped)."
echo ""

# The agent's HTTPS_PROXY is set. curl should tunnel through.
code=$(curl -s -o /dev/null -w "%{http_code}" --cacert "$CA" -u "$AUTH" \
    "https://gateway:443/repository/trusted/test-pkg.txt")
check "HTTPS proxy: trusted accessible" "200" "$code"

code=$(curl -s -o /dev/null -w "%{http_code}" --cacert "$CA" -u "$AUTH" \
    "https://gateway:443/repository/untrusted/test-pkg.txt")
check "HTTPS proxy: untrusted blocked" "403" "$code"

echo ""

# ─── Layer 2: Wrapper forces scoped registry ────────────────
echo "--- Layer 2: Agent wrapper forces scoped registry ─────"
echo ""

registry=$(npm-safe config get registry 2>/dev/null | tr -d '[:space:]')
echo "    npm-safe registry: $registry"
check "wrapper sets scoped registry" "yes" "$([ -n "$registry" ] && echo yes || echo no)"

# Direct npm uses default (not scoped)
direct=$(npm config get registry 2>/dev/null | tr -d '[:space:]')
check "direct npm NOT scoped" "yes" "$([ "$direct" != "$registry" ] && echo yes || echo no)"

echo ""

# ─── Layer 3: Permission configs present ────────────────────
echo "--- Layer 3: Permission denylist configs ───────────────"
echo ""

check "opencode.json present" "yes" "$([ -f /config/opencode.json ] && echo yes || echo no)"
check "claude-settings.json present" "yes" "$([ -f /config/claude-settings.json ] && echo yes || echo no)"

# Verify the configs contain the right patterns
opencode_has_deny=$(grep -c "npm install" /config/opencode.json 2>/dev/null || echo 0)
check "opencode denies npm install" "yes" "$([ "$opencode_has_deny" -gt 0 ] && echo yes || echo no)"

claude_has_deny=$(grep -c "npm install" /config/claude-settings.json 2>/dev/null || echo 0)
check "claude denies npm install" "yes" "$([ "$claude_has_deny" -gt 0 ] && echo yes || echo no)"

echo ""

# ─── Layer 4: CA cert validation ────────────────────────────
echo "--- Layer 4: TLS validation (CA trust) ────────────────"
echo ""

check "CA cert installed" "yes" "$([ -f "$CA" ] && echo yes || echo no)"

# Verify TLS connection validates (no --insecure)
tls_verify=$(curl -s -o /dev/null -w "%{ssl_verify_result}" --cacert "$CA" \
    "https://gateway:443/" 2>/dev/null)
check "TLS cert validates" "0" "$tls_verify"

echo ""

# ─── Summary ────────────────────────────────────────────────
echo "======================================================"
echo "  Agent-side results: $PASS passed, $FAIL failed"
echo "======================================================"
echo ""

if [ "$FAIL" -eq 0 ]; then
    echo "  All agent-side layers work together:"
    echo "  1. HTTPS traffic routes through CONNECT proxy"
    echo "  2. Proxy rewrites port without TLS inspection"
    echo "  3. nginx/Nexus gates untrusted repos on scoped port"
    echo "  4. Wrapper forces scoped registry for npm"
    echo "  5. Permission configs deny direct package installs"
    echo "  6. TLS validates with self-signed CA"
fi
echo ""
