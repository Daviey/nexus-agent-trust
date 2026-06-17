# PoC 4: Agent Wrappers + Permission Denylists

[Back to overview](../README.md)

Client-side enforcement. Wrapper scripts force the scoped registry URL.
Permission configs deny direct package installs and only allow wrappers.

## What it demonstrates

```mermaid
graph TD
    AGENT["Agent tries<br/>'npm install lodash'"]

    CHECK{"Permission system<br/>checks deny list"}
    AGENT --> CHECK

    CHECK -->|"npm install* matches"| DENY["DENIED<br/>agent must use wrapper"]
    CHECK -->|"npm-safe* matches"| ALLOW["ALLOWED"]

    DENY --> WRAPPER["npm-safe install lodash"]
    WRAPPER -->|"sets npm_config_registry"| SCOPED["Scoped Nexus registry<br/>trusted repos only"]

    ALLOW --> WRAPPER

    style DENY fill:#fdd,stroke:#c00
    style ALLOW fill:#dfd,stroke:#0a0
```

## Running

```bash
cd 05-wrappers/
docker-compose up --abort-on-container-exit
docker logs wrapper-tester
```

## Deploying to real workstations

Copy the wrapper scripts and permission configs:

```bash
# Wrappers
sudo cp wrappers/npm-safe /usr/local/bin/
sudo cp wrappers/pip-safe /usr/local/bin/
sudo cp wrappers/go-safe /usr/local/bin/
sudo chmod +x /usr/local/bin/*-safe

# opencode
mkdir -p ~/.config/opencode
cp opencode.json ~/.config/opencode/opencode.json

# Claude Code
mkdir -p ~/.claude
cp claude-settings.json ~/.claude/settings.json
```

Set the scoped registry URL:

```bash
# In the agent's environment (or wrapper defaults)
export NPM_SCOPED_REGISTRY=https://nexus.corp/repository/npm-agent/
```

## Files

- `wrappers/npm-safe`: forces `npm_config_registry` env var
- `wrappers/pip-safe`: forces `--index-url`
- `wrappers/go-safe`: forces `GOPROXY`
- `opencode.json`: permission deny/allow rules for opencode
- `claude-settings.json`: permission deny/allow rules for Claude Code
- `test/run-tests.sh`: 10 tests
- `test/mock-registry.conf`: nginx mock for testing
