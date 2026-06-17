#!/bin/sh
set -e

NEXUS="http://nexus:8081"
ADMIN_USER="admin"

echo "[init] waiting for Nexus to be writable..."
for i in $(seq 1 120); do
    if curl -sf "$NEXUS/service/rest/v1/status/writable" >/dev/null 2>&1; then
        echo "[init] Nexus is writable (after ${i}x5s)"
        break
    fi
    if [ $i -eq 120 ]; then
        echo "[init] ERROR: Nexus not ready after 600s"
        exit 1
    fi
    sleep 5
done

# Read the initial admin password from the mounted volume
PASS_FILE="/nexus-data/admin.password"
if [ -f "$PASS_FILE" ]; then
    ADMIN_PASS=$(cat "$PASS_FILE" | tr -d '[:space:]')
    echo "[init] found initial admin password"
else
    echo "[init] no admin.password file, using admin123"
    ADMIN_PASS="admin123"
fi

AUTH="$ADMIN_USER:$ADMIN_PASS"

# Accept the Community Edition EULA (required in Nexus 3.71+)
# The disclaimer text must be included verbatim in the request body.
echo "[init] accepting EULA..."
cat > /tmp/eula.json << 'ENDJSON'
{"accepted": true, "disclaimer": "Use of Sonatype Nexus Repository - Community Edition is governed by the End User License Agreement at https://links.sonatype.com/products/nxrm/ce-eula. By returning the value from \u2018accepted:false\u2019 to \u2018accepted:true\u2019, you acknowledge that you have read and agree to the End User License Agreement at https://links.sonatype.com/products/nxrm/ce-eula."}
ENDJSON
eula_code=$(curl -s -o /dev/null -w "%{http_code}" -u "$AUTH" -X POST \
    "$NEXUS/service/rest/v1/system/eula" \
    -H "Content-Type: application/json" \
    -d @/tmp/eula.json)
if [ "$eula_code" = "204" ] || [ "$eula_code" = "200" ]; then
    echo "[init] EULA accepted (HTTP $eula_code)"
else
    echo "[init] EULA acceptance returned HTTP $eula_code (may not be required)"
fi

# Change password to admin123 for PoC simplicity (if not already set)
if [ -f "$PASS_FILE" ]; then
    echo "[init] resetting admin password..."
    curl -sf -u "$AUTH" -X PUT \
        "$NEXUS/service/rest/v1/security/users/admin/change-password" \
        -H "Content-Type: text/plain" \
        -d 'admin123' >/dev/null 2>&1
    ADMIN_PASS="admin123"
    AUTH="$ADMIN_USER:$ADMIN_PASS"
fi

# Verify auth
code=$(curl -s -o /dev/null -w "%{http_code}" -u "$AUTH" "$NEXUS/service/rest/v1/status/check")
if [ "$code" != "200" ]; then
    echo "[init] ERROR: auth failed (HTTP $code)"
    exit 1
fi
echo "[init] authenticated as admin"

# ─── Create repositories ─────────────────────────────────────

create_repo() {
    name="$1"
    echo "[init] creating raw hosted repo: $name"
    curl -s -o /dev/null -w "HTTP %{http_code}" -u "$AUTH" -X POST \
        "$NEXUS/service/rest/v1/repositories/raw/hosted" \
        -H "Content-Type: application/json" \
        -d "{
            \"name\": \"$name\",
            \"online\": true,
            \"storage\": {
                \"blobStoreName\": \"default\",
                \"strictContentTypeValidation\": false,
                \"writePolicy\": \"ALLOW_ONCE\"
            }
        }"
    echo ""
}

create_repo "trusted"
create_repo "untrusted"

# ─── Upload test artifacts ───────────────────────────────────

echo "[init] uploading test artifacts..."
echo "this is a TRUSTED artifact (safe for agents)" > /tmp/trusted-pkg.txt
echo "this is an UNTRUSTED artifact (blocked for agents)" > /tmp/untrusted-pkg.txt

curl -sf -u "$AUTH" --upload-file /tmp/trusted-pkg.txt \
    "$NEXUS/repository/trusted/test-pkg.txt" && echo "[init] uploaded trusted/test-pkg.txt"
curl -sf -u "$AUTH" --upload-file /tmp/untrusted-pkg.txt \
    "$NEXUS/repository/untrusted/test-pkg.txt" && echo "[init] uploaded untrusted/test-pkg.txt"

# ─── Verify ──────────────────────────────────────────────────

echo "[init] verifying repos..."
for repo in trusted untrusted; do
    code=$(curl -s -o /dev/null -w "%{http_code}" \
        -u "$AUTH" "$NEXUS/repository/$repo/test-pkg.txt")
    echo "[init]   $repo/test-pkg.txt -> HTTP $code"
done

echo "[init] setup complete"
