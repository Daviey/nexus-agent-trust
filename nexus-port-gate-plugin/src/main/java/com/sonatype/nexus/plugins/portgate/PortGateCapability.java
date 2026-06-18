package com.sonatype.nexus.plugins.portgate;

import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import org.sonatype.nexus.capability.CapabilitySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nexus Capability managing one scoped port.
 *
 * Create multiple instances for different agent contexts:
 * <ul>
 *   <li>"CI Agents" on port 8443 with limited repos</li>
 *   <li>"Dev Agents" on port 8444 with broader access</li>
 * </ul>
 *
 * Properties persisted in the Nexus database:
 * <ul>
 *   <li>{@code scopedPort} - TCP port to gate</li>
 *   <li>{@code enabledRepos} - comma-separated repo names from the UI picker</li>
 *   <li>{@code auditMode} - "true" to log without blocking</li>
 *   <li>{@code readOnly} - "true" to block write methods</li>
 * </ul>
 */
@Named("agent-trust.port-gate")
@Singleton
public class PortGateCapability extends CapabilitySupport<PortGateCapability.Cfg>
{
    public static final String TYPE_ID = "agent-trust.port-gate";
    private static final Logger log = LoggerFactory.getLogger(PortGateCapability.class);
    private static final int DEFAULT_PORT = 8443;

    private final PortGateConfig portGateConfig;

    @Inject
    public PortGateCapability(PortGateConfig portGateConfig) {
        this.portGateConfig = portGateConfig;
    }

    @Override
    protected Cfg createConfig(Map<String, String> p) throws Exception {
        int port = DEFAULT_PORT;
        try {
            port = Integer.parseInt(p.getOrDefault("scopedPort", String.valueOf(DEFAULT_PORT)).trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid scopedPort, using default {}", DEFAULT_PORT);
        }
        boolean audit = Boolean.parseBoolean(p.getOrDefault("auditMode", "false"));
        boolean readOnly = Boolean.parseBoolean(p.getOrDefault("readOnly", "false"));
        List<String> prefixes = PortGateConfig.parseRepos(p.getOrDefault("enabledRepos", ""));
        return new Cfg(port, prefixes, audit, readOnly);
    }

    @Override
    protected void onUpdate(Cfg c) throws Exception {
        portGateConfig.update(c.scopedPort, c.allowedPrefixes, c.auditMode, c.readOnly);
        log.info("Port-gate updated: port={} | {} repos | audit={} | readOnly={}",
            c.scopedPort, c.allowedPrefixes.size(), c.auditMode, c.readOnly);
    }

    @Override
    protected void onActivate(Cfg c) throws Exception {
        portGateConfig.update(c.scopedPort, c.allowedPrefixes, c.auditMode, c.readOnly);
        log.info("Port-gate activated: port={} | {} repos | audit={} | readOnly={}",
            c.scopedPort, c.allowedPrefixes.size(), c.auditMode, c.readOnly);
    }

    @Override
    protected void onPassivate(Cfg c) throws Exception {
        portGateConfig.remove(c.scopedPort);
        log.info("Port-gate deactivated: port={}", c.scopedPort);
    }

    @Override
    protected void onRemove(Cfg c) throws Exception {
        portGateConfig.remove(c.scopedPort);
        log.info("Port-gate removed: port={}", c.scopedPort);
    }

    @Override
    protected String renderDescription() {
        Cfg c = getConfig();
        if (c == null) return "Not configured";
        return "Port " + c.scopedPort + " | " + c.allowedPrefixes.size() + " repo(s)"
            + (c.auditMode ? " | AUDIT" : "") + (c.readOnly ? " | READ-ONLY" : "");
    }

    static final class Cfg
    {
        final int scopedPort;
        final List<String> allowedPrefixes;
        final boolean auditMode;
        final boolean readOnly;

        Cfg(int port, List<String> prefixes, boolean audit, boolean readOnly) {
            this.scopedPort = port;
            this.allowedPrefixes = prefixes;
            this.auditMode = audit;
            this.readOnly = readOnly;
        }
    }
}
