#!/usr/bin/env python3
"""
Port-rewriting forward proxy for the Nexus agent-trust PoC.

This proxy operates in two modes:

1. HTTP (plain): the client sends the full URL (GET http://gateway:8080/...).
   We rewrite port 8080 -> 8082 and forward to the gateway's scoped listener.

2. HTTPS (CONNECT): the client sends CONNECT gateway:8080. We rewrite to
   CONNECT gateway:8082, open a TCP tunnel, and pipe bytes both ways without
   inspecting TLS. This demonstrates the no-MITM approach from the paper.

No TLS inspection occurs. The proxy only touches the destination port.
"""

import http.server
import os
import select
import socket
import socketserver
import ssl
import sys
import threading
import urllib.request
import urllib.error

LISTEN_HOST = "0.0.0.0"
LISTEN_PORT = 3128

# In the docker network, the gateway is reachable as "gateway"
GATEWAY_HOST = "gateway"
FULL_PORT = int(os.environ.get("FULL_PORT", "8080"))
SCOPED_PORT = int(os.environ.get("SCOPED_PORT", "8082"))

LOG_PREFIX = "[forward-proxy]"


def log(msg):
    print(f"{LOG_PREFIX} {msg}", flush=True)


class HTTPProxyHandler(http.server.BaseHTTPRequestHandler):
    """Handles plain HTTP proxy requests (GET http://host:port/path)."""

    def do_GET(self):
        self._proxy_http("GET")

    def do_HEAD(self):
        self._proxy_http("HEAD")

    def do_POST(self):
        self._proxy_http("POST")

    def do_PUT(self):
        self._proxy_http("PUT")

    def do_DELETE(self):
        self._proxy_http("DELETE")

    def _proxy_http(self, method):
        url = self.path
        rewritten = rewrite_port(url)
        if rewritten != url:
            log(f"REWRITE {url} -> {rewritten}")
        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length) if content_length > 0 else None

        try:
            req = urllib.request.Request(
                rewritten,
                data=body,
                method=method,
                headers={k: v for k, v in self.headers.items()},
            )
            with urllib.request.urlopen(req, timeout=30) as resp:
                self.send_response(resp.status)
                for key, value in resp.headers.items():
                    if key.lower() not in ("transfer-encoding", "connection"):
                        self.send_header(key, value)
                self.end_headers()
                self.wfile.write(resp.read())
        except urllib.error.HTTPError as e:
            self.send_response(e.code)
            for key, value in e.headers.items():
                if key.lower() not in ("transfer-encoding", "connection"):
                    self.send_header(key, value)
            self.end_headers()
            self.wfile.write(e.read())
        except Exception as e:
            log(f"ERROR {method} {rewritten}: {e}")
            self.send_response(502)
            self.end_headers()
            self.wfile.write(str(e).encode())

    def do_CONNECT(self):
        """Handle HTTPS CONNECT tunneling with port rewrite (no MITM)."""
        host, port = parse_connect_target(self.path)
        original = f"{host}:{port}"

        # Only rewrite traffic destined for the gateway, not all FULL_PORT traffic
        if port == FULL_PORT and is_gateway_host(host):
            new_port = SCOPED_PORT
            log(f"CONNECT REWRITE {original} -> {host}:{new_port}")
        else:
            new_port = port

        try:
            remote = socket.create_connection((host, new_port), timeout=10)
        except Exception as e:
            log(f"CONNECT FAIL {host}:{new_port}: {e}")
            self.send_response(502)
            self.end_headers()
            return

        self.send_response(200, "Connection Established")
        self.end_headers()

        # Pipe bytes both ways. No TLS inspection.
        self._tunnel(self.connection, remote)

    def _tunnel(self, client, remote):
        sockets = [client, remote]
        try:
            while True:
                ready, _, _ = select.select(sockets, [], [], 60)
                if not ready:
                    break
                for sock in ready:
                    data = sock.recv(65536)
                    if not data:
                        return
                    other = remote if sock is client else client
                    other.sendall(data)
        except Exception:
            pass
        finally:
            remote.close()

    def log_message(self, format, *args):
        log(f"{self.client_address[0]} {format % args}")


def is_gateway_host(host):
    """Check if host is the gateway (exact match or subdomain)."""
    return host == GATEWAY_HOST or host.endswith("." + GATEWAY_HOST)


def rewrite_port(url):
    """Rewrite gateway's full port to scoped port in a URL string."""
    if GATEWAY_HOST not in url:
        return url
    return url.replace(f":{FULL_PORT}", f":{SCOPED_PORT}")


def parse_connect_target(path):
    """Parse 'host:port' from a CONNECT request line."""
    if ":" in path:
        host, port = path.rsplit(":", 1)
        return host, int(port)
    return path, 443


class ThreadingHTTPProxy(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    allow_reuse_address = True


if __name__ == "__main__":
    server = ThreadingHTTPProxy((LISTEN_HOST, LISTEN_PORT), HTTPProxyHandler)
    log(f"listening on {LISTEN_HOST}:{LISTEN_PORT}")
    log(f"rewrites {GATEWAY_HOST}:{FULL_PORT} -> {GATEWAY_HOST}:{SCOPED_PORT}")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        log("shutting down")
        server.shutdown()
