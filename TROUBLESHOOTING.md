# Troubleshooting

Common issues and fixes for the Nexus Agent-Trust PoCs.

## Table of contents

- [Nexus won't start](#nexus-wont-start)
- [Port conflicts](#port-conflicts)
- [EULA / 403 errors](#eula--403-errors)
- [Repo creation returns 404](#repo-creation-returns-404)
- [Forward proxy rewrites non-gateway traffic](#forward-proxy-rewrites-non-gateway-traffic)
- [TLS / certificate errors](#tls--certificate-errors)
- [npm-safe not found in PATH](#npm-safe-not-found-in-path)
- [apk add fails in containers](#apk-add-fails-in-containers)
- [Nexus scripting API returns 410](#nexus-scripting-api-returns-410)

## Nexus won't start

**Symptom:** The `nexus-init` container fails with "auth failed" or "timeout".

**Cause:** Nexus takes 60 to 120 seconds to become writable. The init script
polls every 5 seconds for up to 600 seconds, but on slow machines this may
not be enough.

**Fix:**

```bash
# Check Nexus is actually running
docker logs poc-nexus | tail -20

# Look for "Started Sonatype Nexus OSS"
# If not started yet, wait and retry the init:

docker-compose down -v
docker-compose up -d
```

## Port conflicts

**Symptom:** `Bind for 0.0.0.0:PORT failed: port is already allocated`

**Cause:** Another process on the host is using the same port.

**Fix:** Each PoC uses different host port mappings to avoid conflicts:

| PoC | Nexus UI | Full access | Scoped access | Forward proxy |
|---|---|---|---|---|
| 01-http-baseline | 18081 | 28080 | 28082 | 23128 |
| 02-https-connect | 18081 | 34443 | 38443 | 3128 |
| 04-mtls | | 6443 | | |
| 06-full | | 443 | 58443 | 53128 |

If you need different ports, edit `docker-compose.yml` in the relevant PoC
directory. Only change the host-side port (left of the colon).

## EULA / 403 errors

**Symptom:** Nexus returns 403 with "You must accept the End User License
Agreement" on all operations.

**Cause:** Nexus 3.71+ (Community Edition) requires EULA acceptance before
any repository operations. The init script handles this automatically, but
if the EULA text changes between versions, the acceptance may fail.

**Fix:**

```bash
# Check if EULA was accepted
docker exec <nexus-container> curl -s -u admin:admin123 \
    http://localhost:8081/service/rest/v1/system/eula

# If "accepted: false", accept manually:
docker exec <nexus-container> curl -s -u admin:admin123 -X POST \
    http://localhost:8081/service/rest/v1/system/eula \
    -H "Content-Type: application/json" \
    -d '{"accepted": true, "disclaimer": "USE THE DISCLAIMER FROM THE GET RESPONSE"}'
```

The EULA endpoint is `POST /service/rest/v1/system/eula` (not PUT). The
disclaimer text must be included verbatim from the GET response.

## Repo creation returns 404

**Symptom:** The init script logs `HTTP 404` when creating repositories.

**Cause:** Nexus 3.x uses `POST` to the collection endpoint
(`/service/rest/v1/repositories/raw/hosted`), not `PUT` to a named
endpoint. The repository name goes in the JSON body, not the URL.

**Fix:** The init scripts in this repo already use the correct `POST` method.
If you see 404, check that the curl command uses `-X POST` and the body
includes `"name"`.

## Forward proxy rewrites non-gateway traffic

**Symptom:** The forward proxy rewrites all port 443 traffic, breaking
package downloads (e.g. `apk add curl` fails inside agent containers).

**Cause:** Early versions of the proxy checked only the port number, not the
destination hostname. When `FULL_PORT=443`, all HTTPS traffic was rewritten.

**Fix:** The proxy now checks `is_gateway_host()` before rewriting. Only
traffic destined for the `gateway` hostname is rewritten. If you forked the
proxy, ensure the `do_CONNECT` method includes this check:

```python
if port == FULL_PORT and is_gateway_host(host):
    new_port = SCOPED_PORT
```

## TLS / certificate errors

**Symptom:** `curl: (60) SSL certificate problem: unable to get local issuer
certificate`

**Cause:** The self-signed CA cert is not installed in the client's trust
store.

**Fix:** Each PoC that uses HTTPS mounts the CA cert into the test container.
If testing manually from the host:

```bash
# Use --cacert to specify the CA
curl --cacert 02-https-connect/certs/ca-cert.pem https://localhost:34443/

# Or install the CA system-wide (Linux)
sudo cp 02-https-connect/certs/ca-cert.pem /usr/local/share/ca-certificates/poc-ca.crt
sudo update-ca-certificates

# Or for npm specifically
export NODE_EXTRA_CA_CERTS=/path/to/ca-cert.pem
```

## npm-safe not found in PATH

**Symptom:** `sh: npm-safe: not found`

**Cause:** The wrapper scripts are mounted but the `PATH` environment variable
does not include the mount point.

**Fix:** Ensure the container's `PATH` includes the wrappers directory. In
`docker-compose.yml`:

```yaml
environment:
  - PATH=/wrappers:/usr/local/bin:/usr/bin:/bin:/sbin
```

Note: `/sbin` is required on Alpine for `apk` to work.

## apk add fails in containers

**Symptom:** `sh: apk: not found` in node:20-alpine containers.

**Cause:** The `PATH` override excludes `/sbin` where `apk` lives on Alpine.

**Fix:** Add `/sbin` to the `PATH` environment variable:

```yaml
environment:
  - PATH=/wrappers:/usr/local/bin:/usr/bin:/bin:/sbin
```

## Nexus scripting API returns 410

**Symptom:** `POST /service/rest/v1/script` returns `410 Gone`.

**Cause:** Nexus 3.71+ removed the Groovy scripting API for security reasons.
The `port-gate.groovy` script in `03-plugin/` only works on Nexus < 3.71.

**Fix:** Use the Java plugin approach instead. Build the OSGi bundle with
Maven (see `03-plugin/nexus-port-gate/Dockerfile.build`) and deploy the JAR
to `$NEXUS_HOME/deploy/`. For PoC purposes, the nginx gateway stands in for
the plugin and proves the same concept.
