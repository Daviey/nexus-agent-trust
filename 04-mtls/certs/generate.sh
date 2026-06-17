#!/bin/sh
# Generate CA, server cert, and client cert for mTLS PoC.
# The client cert (CN=agent-proxy) identifies agent traffic.

set -e
cd "$(dirname "$0")"

# 1. CA
openssl req -x509 -newkey rsa:4096 -keyout ca-key.pem -out ca-cert.pem \
    -days 365 -nodes -subj "/CN=PoC-mTLS-CA" 2>/dev/null

# 2. Server cert (for nginx)
openssl req -newkey rsa:4096 -keyout server-key.pem -out server-csr.pem \
    -nodes -subj "/CN=gateway" 2>/dev/null
openssl x509 -req -in server-csr.pem -CA ca-cert.pem -CAkey ca-key.pem \
    -CAcreateserial -out server-cert.pem -days 365 \
    -extfile <(printf "subjectAltName=DNS:gateway,DNS:localhost,DNS:nexus") 2>/dev/null

# 3. Client cert (CN=agent-proxy, identifies the agent context)
openssl req -newkey rsa:4096 -keyout client-key.pem -out client-csr.pem \
    -nodes -subj "/CN=agent-proxy/O=PoC" 2>/dev/null
openssl x509 -req -in client-csr.pem -CA ca-cert.pem -CAkey ca-key.pem \
    -CAcreateserial -out client-cert.pem -days 365 2>/dev/null

echo "[certs] CA:         ca-cert.pem"
echo "[certs] Server:     server-cert.pem (CN=gateway)"
echo "[certs] Client:     client-cert.pem (CN=agent-proxy)"
echo "[certs] done"
