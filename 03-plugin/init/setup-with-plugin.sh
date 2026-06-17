#!/bin/sh
set -e

NEXUS="http://nexus:8081"
AUTH="admin:admin123"

echo "[init] waiting for Nexus..."
for i in $(seq 1 120); do
    if curl -sf "$NEXUS/service/rest/v1/status/writable" >/dev/null 2>&1; then
        echo "[init] Nexus ready (${i}x5s)"
        break
    fi
    [ $i -eq 120 ] && { echo "[init] ERROR: timeout"; exit 1; }
    sleep 5
done

# ─── Password setup ─────────────────────────────────────────
PASS_FILE="/nexus-data/admin.password"
if [ -f "$PASS_FILE" ]; then
    PASS=$(cat "$PASS_FILE" | tr -d '[:space:]')
    AUTH_TEMP="admin:$PASS"
else
    AUTH_TEMP="$AUTH"
fi

# Accept EULA
cat > /tmp/eula.json << 'ENDJSON'
{"accepted": true, "disclaimer": "Use of Sonatype Nexus Repository - Community Edition is governed by the End User License Agreement at https://links.sonatype.com/products/nxrm/ce-eula. By returning the value from \u2018accepted:false\u2019 to \u2018accepted:true\u2019, you acknowledge that you have read and agree to the End User License Agreement at https://links.sonatype.com/products/nxrm/ce-eula."}
ENDJSON
curl -sf -u "$AUTH_TEMP" -X POST "$NEXUS/service/rest/v1/system/eula" \
    -H "Content-Type: application/json" -d @/tmp/eula.json >/dev/null 2>&1 || true

# Reset password
if [ -f "$PASS_FILE" ]; then
    curl -sf -u "$AUTH_TEMP" -X PUT \
        "$NEXUS/service/rest/v1/security/users/admin/change-password" \
        -H "Content-Type: text/plain" -d 'admin123' >/dev/null 2>&1
fi

# Verify auth
code=$(curl -s -o /dev/null -w "%{http_code}" -u "$AUTH" "$NEXUS/service/rest/v1/status/check")
[ "$code" = "200" ] || { echo "[init] auth failed ($code)"; exit 1; }
echo "[init] authenticated"

# ─── Create repos and upload artifacts ──────────────────────
echo "[init] creating repos..."
for repo in trusted untrusted; do
    curl -sf -u "$AUTH" -X POST \
        "$NEXUS/service/rest/v1/repositories/raw/hosted" \
        -H "Content-Type: application/json" \
        -d "{\"name\":\"$repo\",\"online\":true,\"storage\":{\"blobStoreName\":\"default\",\"strictContentTypeValidation\":false,\"writePolicy\":\"ALLOW_ONCE\"}}" \
        >/dev/null 2>&1 || true
done

echo "trusted" > /tmp/t.txt
echo "untrusted" > /tmp/u.txt
curl -sf -u "$AUTH" --upload-file /tmp/t.txt "$NEXUS/repository/trusted/test-pkg.txt" >/dev/null 2>&1
curl -sf -u "$AUTH" --upload-file /tmp/u.txt "$NEXUS/repository/untrusted/test-pkg.txt" >/dev/null 2>&1
echo "[init] repos configured"

# ─── Upload the port-gate Groovy script ─────────────────────
echo "[init] uploading port-gate Groovy script..."

# Build proper JSON with jq (handles all escaping)
GROOVY_CONTENT=$(cat /scripts/port-gate.groovy)
jq -n \
    --arg name "port-gate" \
    --arg content "$GROOVY_CONTENT" \
    '{name: $name, type: "groovy", content: $content}' > /tmp/script-create.json

# Create or update the script
curl -sf -u "$AUTH" -X POST \
    "$NEXUS/service/rest/v1/script" \
    -H "Content-Type: application/json" \
    -d @/tmp/script-create.json >/dev/null 2>&1 \
    && echo "[init] script created" \
    || {
        curl -sf -u "$AUTH" -X PUT \
            "$NEXUS/service/rest/v1/script/port-gate" \
            -H "Content-Type: application/json" \
            -d @/tmp/script-create.json >/dev/null 2>&1 \
            && echo "[init] script updated"
    }

# ─── Run the script ─────────────────────────────────────────
echo "[init] running port-gate script..."
RESULT=$(curl -sf -u "$AUTH" -X POST \
    "$NEXUS/service/rest/v1/script/port-gate/run" \
    -H "Content-Type: application/json" \
    -d '{}' 2>&1)

echo "[init] script result:"
echo "$RESULT" | jq -r '.result // .error // "no result"' 2>/dev/null | sed 's/^/  /' || echo "$RESULT" | sed 's/^/  /'

echo "[init] done"
