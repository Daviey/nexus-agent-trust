#!/bin/sh
# ─────────────────────────────────────────────────────────────
# PoC 5: Full End-to-End Test (Human Side)
#
# The human-tester has NO proxy, NO wrappers. Direct HTTPS access
# to the full port (443). Should get everything.
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
echo "  PoC 5: Full End-to-End (Human Perspective)"
echo "======================================================"
echo ""

echo "--- Human: direct HTTPS, no proxy, full access ────────"
echo ""

code=$(curl -s -o /dev/null -w "%{http_code}" --cacert "$CA" -u "$AUTH" \
    "https://gateway:443/repository/trusted/test-pkg.txt")
check "human reads trusted" "200" "$code"

code=$(curl -s -o /dev/null -w "%{http_code}" --cacert "$CA" -u "$AUTH" \
    "https://gateway:443/repository/untrusted/test-pkg.txt")
check "human reads untrusted" "200" "$code"

echo ""

echo "======================================================"
echo "  Human-side results: $PASS passed, $FAIL failed"
echo "======================================================"
echo ""

if [ "$FAIL" -eq 0 ]; then
    echo "  Human gets full access on port 443."
    echo "  No proxy, no wrappers, no restrictions."
fi
echo ""
