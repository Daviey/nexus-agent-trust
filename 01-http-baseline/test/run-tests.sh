#!/bin/sh
# ─────────────────────────────────────────────────────────────
# PoC Test Suite — demonstrates port-based trust scoping.
#
# The tests prove three scenarios from the paper:
#
#   1. HUMAN (direct, port 8080) → full access to all repos
#   2. AGENT (scoped port 8082)  → only trusted repos
#   3. AGENT via FORWARD PROXY   → port rewritten to 8082, scoped
#
# ─────────────────────────────────────────────────────────────

PASS=0
FAIL=0
AUTH="admin:admin123"

log() { echo "$1"; }

check() {
    desc="$1"
    expected="$2"
    actual="$3"
    if [ "$actual" = "$expected" ]; then
        echo "  ✅ PASS: $desc (HTTP $actual)"
        PASS=$((PASS + 1))
    else
        echo "  ❌ FAIL: $desc — expected HTTP $expected, got HTTP $actual"
        FAIL=$((FAIL + 1))
    fi
}

echo ""
echo "════════════════════════════════════════════════════════════"
echo "  Nexus Agent-Trust PoC — Test Suite"
echo "════════════════════════════════════════════════════════════"
echo ""

# ─── Scenario 1: Human direct access (port 8080) ─────────────
echo "─── Scenario 1: HUMAN via full port (8080) ────────────────"
echo "    Expected: both trusted and untrusted repos accessible"
echo ""

code=$(curl -s -o /dev/null -w "%{http_code}" -u "$AUTH" \
    "http://gateway:8080/repository/trusted/test-pkg.txt")
check "human reads trusted" "200" "$code"

code=$(curl -s -o /dev/null -w "%{http_code}" -u "$AUTH" \
    "http://gateway:8080/repository/untrusted/test-pkg.txt")
check "human reads untrusted" "200" "$code"

echo ""

# ─── Scenario 2: Agent scoped access (port 8082 direct) ──────
echo "─── Scenario 2: AGENT via scoped port (8082 direct) ───────"
echo "    Expected: trusted OK, untrusted BLOCKED"
echo ""

code=$(curl -s -o /dev/null -w "%{http_code}" -u "$AUTH" \
    "http://gateway:8082/repository/trusted/test-pkg.txt")
check "agent reads trusted" "200" "$code"

code=$(curl -s -o /dev/null -w "%{http_code}" -u "$AUTH" \
    "http://gateway:8082/repository/untrusted/test-pkg.txt")
check "agent blocked from untrusted" "403" "$code"

echo ""

# ─── Scenario 3: Agent via forward proxy (port rewrite) ──────
echo "─── Scenario 3: AGENT via FORWARD PROXY (port 8080→8082) ──"
echo "    Client requests port 8080, proxy rewrites to 8082"
echo "    Expected: trusted OK, untrusted BLOCKED"
echo ""

code=$(curl -s -o /dev/null -w "%{http_code}" \
    -x "http://forward-proxy:3128" \
    -u "$AUTH" \
    "http://gateway:8080/repository/trusted/test-pkg.txt")
check "agent-via-proxy reads trusted" "200" "$code"

code=$(curl -s -o /dev/null -w "%{http_code}" \
    -x "http://forward-proxy:3128" \
    -u "$AUTH" \
    "http://gateway:8080/repository/untrusted/test-pkg.txt")
check "agent-via-proxy blocked from untrusted" "403" "$code"

echo ""

# ─── Summary ─────────────────────────────────────────────────
echo "════════════════════════════════════════════════════════════"
echo "  Results: $PASS passed, $FAIL failed"
echo "════════════════════════════════════════════════════════════"
echo ""

if [ "$FAIL" -gt 0 ]; then
    echo "  ⚠️  Some tests failed. The PoC demonstrates the concept"
    echo "  but the enforcement did not work as expected."
    exit 1
else
    echo "  ✅ All tests passed. The port-based trust scoping"
    echo "  architecture works as described in the paper."
    echo ""
    echo "  Key takeaways:"
    echo "  • Port 8080 (full):     humans get access to ALL repos"
    echo "  • Port 8082 (scoped):   only allowlisted repos accessible"
    echo "  • Forward proxy:        rewrites 8080→8082 transparently"
    echo "  • No MITM required:     the proxy only touches the port"
fi

echo ""
