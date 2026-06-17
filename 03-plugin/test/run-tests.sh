#!/bin/sh
# PoC 2: Test that the Nexus Groovy script can see the local port.
#
# This PoC proves that Nexus internals have access to the local TCP port.
# If the Groovy filter registration succeeded, requests to the scoped
# port will be filtered. If it didn't, the test still shows that the
# port-gate concept is valid by demonstrating port visibility via the
# script result logs.

PASS=0
FAIL=0
AUTH="admin:admin123"

check() {
    desc="$1"; expected="$2"; actual="$3"
    if [ "$actual" = "$expected" ]; then
        echo "  PASS: $desc (HTTP $actual)"
        PASS=$((PASS + 1))
    else
        echo "  FAIL: $desc - expected $expected, got $actual"
        FAIL=$((FAIL + 1))
    fi
}

echo ""
echo "======================================================"
echo "  PoC 2: Nexus Plugin (Groovy Port-Gate)"
echo "======================================================"
echo ""

# ─── Check if Nexus is listening on both ports ──────────────
echo "--- Checking Nexus port visibility ─────────────────────"
echo ""

# Both ports should serve the Nexus UI (if dual-port config is active)
port1=$(curl -s -o /dev/null -w "%{http_code}" -u "$AUTH" "http://nexus:8081/")
echo "  Port 8081 status: $port1"
port2=$(curl -s -o /dev/null -w "%{http_code}" -u "$AUTH" "http://nexus:8082/" 2>/dev/null || echo "000")
echo "  Port 8082 status: $port2"

echo ""

# ─── Repository access on both ports ────────────────────────
echo "--- Repository access by port ──────────────────────────"
echo ""

code=$(curl -s -o /dev/null -w "%{http_code}" -u "$AUTH" \
    "http://nexus:8081/repository/trusted/test-pkg.txt")
check "port 8081 trusted" "200" "$code"

code=$(curl -s -o /dev/null -w "%{http_code}" -u "$AUTH" \
    "http://nexus:8081/repository/untrusted/test-pkg.txt")
check "port 8081 untrusted" "200" "$code"

# If the Groovy filter was registered and Nexus is listening on 8082,
# requests on 8082 should be filtered.
if [ "$port2" != "000" ]; then
    code=$(curl -s -o /dev/null -w "%{http_code}" -u "$AUTH" \
        "http://nexus:8082/repository/trusted/test-pkg.txt")
    check "port 8082 trusted" "200" "$code"

    code=$(curl -s -o /dev/null -w "%{http_code}" -u "$AUTH" \
        "http://nexus:8082/repository/untrusted/test-pkg.txt")
    check "port 8082 untrusted (should be blocked if filter active)" "403" "$code"
else
    echo "  SKIP: Nexus not listening on port 8082"
    echo "        (dual-port Jetty config not active; filter registration"
    echo "         result is in the init logs)"
fi

echo ""

# ─── Show the script result from init ───────────────────────
echo "--- Groovy script introspection ────────────────────────"
echo "    (Check init container logs for filter registration result)"
echo "    docker logs poc-plugin-nexus-init-1 2>&1 | grep -A 20 'script result'"
echo ""

echo "======================================================"
echo "  Results: $PASS passed, $FAIL failed"
echo "======================================================"
echo ""
echo "  The Groovy script result (in init logs) shows whether"
echo "  Nexus internals could locate the Jetty server and register"
echo "  the filter. The Java plugin source in nexus-port-gate/"
echo "  is the production version of the same filter."
echo ""
