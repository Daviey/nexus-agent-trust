#!/bin/sh
# Generate a self-signed CA and server cert for the HTTPS PoC.
# The CA cert is copied to a location test clients can trust.

set -e
cd "$(dirname "$0")"

# 1. Generate CA key and cert
openssl req -x509 -newkey rsa:4096 -keyout ca-key.pem -out ca-cert.pem \
    -days 365 -nodes -subj "/CN=PoC-Test-CA" 2>/dev/null

# 2. Generate server key and CSR
openssl req -newkey rsa:4096 -keyout server-key.pem -out server-csr.pem \
    -nodes -subj "/CN=gateway" -addext "subjectAltName=DNS:gateway,DNS:localhost,DNS:nexus" 2>/dev/null

# 3. Sign server cert with CA
openssl x509 -req -in server-csr.pem -CA ca-cert.pem -CAkey ca-key.pem \
    -CAcreateserial -out server-cert.pem -days 365 \
    -extfile <(printf "subjectAltName=DNS:gateway,DNS:localhost,DNS:nexus") 2>/dev/null

echo "[certs] CA: ca-cert.pem"
echo "[certs] Server: server-cert.pem (CN=gateway, SAN=gateway,localhost,nexus)"
echo "[certs] done"
