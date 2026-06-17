#!/bin/sh
# ─────────────────────────────────────────────────────────────
# PoC 1: HTTPS CONNECT Tunnel Test Suite
#
# Proves that:
#   1. Direct HTTPS (port 4443) gives full access
#   2. Scoped HTTPS (port 8443) blocks untrusted repos
#   3. CONNECT proxy rewrites port WITHOUT inspecting TLS
#      (the proxy never has the server's private key)
#
# ─────────────────────────────────────────────────────────────

PASS=0
FAIL=0
AUTH="admin:admin123"
CA="/etc/ssl/certs/poc-ca.pem"

check() {
    desc="$1"; expected="$2"; actual="$3"
    if [ "$actual" = "$expected" ]; then
        echo "  PASS: $desc (HTTP $actual)"
        PASS=$((PASS + 1))
    else
        echo "  FAIL: $desc - expected HTTP $expected, got HTTP $actual"
        FAIL=$((FAIL + 1))
    fi
}

echo ""
echo "======================================================"
echo "  PoC 1: HTTPS CONNECT Tunnel"
echo "======================================================"
echo ""

# ─── Scenario 1: Human direct HTTPS (port 4443) ─────────────
echo "--- Scenario 1: HUMAN direct HTTPS (port 4443) --------"
echo "    curl --cacert poc-ca.pem https://gateway:4443/..."
echo ""

code=$(curl -s -o /dev/null -w "%{http_code}" --cacert "$CA" -u "$AUTH" \
    "https://gateway:4443/repository/trusted/test-pkg.txt")
check "human reads trusted via HTTPS" "200" "$code"

code=$(curl -s -o /dev/null -w "%{http_code}" --cacert "$CA" -u "$AUTH" \
    "https://gateway:4443/repository/untrusted/test-pkg.txt")
check "human reads untrusted via HTTPS" "200" "$code"

echo ""

# ─── Scenario 2: Agent scoped HTTPS (port 8443) ────────────
echo "--- Scenario 2: AGENT scoped HTTPS (port 8443) --------"
echo "    curl --cacert poc-ca.pem https://gateway:8443/..."
echo ""

code=$(curl -s -o /dev/null -w "%{http_code}" --cacert "$CA" -u "$AUTH" \
    "https://gateway:8443/repository/trusted/test-pkg.txt")
check "agent reads trusted via HTTPS" "200" "$code"

code=$(curl -s -o /dev/null -w "%{http_code}" --cacert "$CA" -u "$AUTH" \
    "https://gateway:8443/repository/untrusted/test-pkg.txt")
check "agent blocked from untrusted via HTTPS" "403" "$code"

echo ""

# ─── Scenario 3: CONNECT proxy (the key test) ───────────────
# The client requests HTTPS to gateway:4443. The proxy intercepts
# the CONNECT request, rewrites it to gateway:8443, and opens a
# TCP tunnel. TLS passes through end-to-end. The proxy NEVER
# decrypts the traffic.
echo "--- Scenario 3: AGENT via CONNECT proxy (4443->8443) ---"
echo "    Client: CONNECT gateway:4443"
echo "    Proxy:  rewrites to gateway:8443 (no TLS inspection)"
echo "    curl -x http://forward-proxy:3128 https://gateway:4443/..."
echo ""

code=$(curl -s -o /dev/null -w "%{http_code}" --cacert "$CA" \
    -x "http://forward-proxy:3128" -u "$AUTH" \
    "https://gateway:4443/repository/trusted/test-pkg.txt")
check "agent via CONNECT reads trusted" "200" "$code"

code=$(curl -s -o /dev/null -w "%{http_code}" --cacert "$CA" \
    -x "http://forward-proxy:3128" -u "$AUTH" \
    "https://gateway:4443/repository/untrusted/test-pkg.txt")
check "agent via CONNECT blocked from untrusted" "403" "$code"

echo ""

# ─── Summary ────────────────────────────────────────────────
echo "======================================================"
echo "  Results: $PASS passed, $FAIL failed"
echo "======================================================"
echo ""

if [ "$FAIL" -eq 0 ]; then
    echo "  All tests passed. The CONNECT proxy rewrites the"
    echo "  destination port without terminating or inspecting"
    echo "  TLS. The server cert is never on the proxy. The"
    echo "  client validates end-to-end with the real CA."
    echo ""
    echo "  Key proof:"
    echo "  * The proxy has NO server cert or private key"
    echo "  * The proxy has NO CA cert"
    echo "  * TLS session is client <-> gateway (end-to-end)"
    echo "  * Proxy only modified the TCP destination port"
fi
echo ""
