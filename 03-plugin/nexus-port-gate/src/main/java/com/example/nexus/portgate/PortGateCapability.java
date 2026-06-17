package com.example.nexus.portgate;

import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.capability.Capability;
import org.sonatype.nexus.capability.CapabilityContext;
import org.sonatype.nexus.capability.CapabilityIdentity;
import org.sonatype.nexus.capability.support.CapabilitySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nexus Capability that manages the port-gate filter configuration.
 *
 * Appears in the Nexus UI under:
 *   Administration > Capabilities > Agent Trust: Port Gate
 *
 * Fields:
 *   scopedPort     - the Nexus port receiving agent traffic (default 8443)
 *   allowedPaths   - one repository path prefix per line, # for comments
 *
 * When the capability is enabled, the filter reads the config on every
 * request. When disabled, the scoped port denies all repository access.
 * Changes take effect on the next request. No restart needed.
 *
 * Config is stored in the Nexus database and survives upgrades.
 */
@Named(PortGateCapability.TYPE_ID)
@Singleton
public class PortGateCapability extends CapabilitySupport<PortGateCapability.Config> {

    public static final String TYPE_ID = "agent-trust.port-gate";

    private static final Logger log = LoggerFactory.getLogger(PortGateCapability.class);

    private final PortGateConfig portGateConfig;

    @Inject
    public PortGateCapability(final PortGateConfig portGateConfig) {
        this.portGateConfig = portGateConfig;
    }

    @Override
    protected Config createConfig(final Map<String, String> properties) {
        return new Config(properties);
    }

    @Override
    protected void onActivate(final CapabilityContext context) {
        log.info("Port-gate capability activated");
        applyConfig(context);
    }

    @Override
    protected void onUpdate(final CapabilityContext context) {
        log.info("Port-gate capability updated");
        applyConfig(context);
    }

    @Override
    protected void onDeactivate(final CapabilityContext context) {
        log.info("Port-gate capability deactivated");
        portGateConfig.clear();
    }

    @Override
    protected void onRemove(final CapabilityContext context) {
        log.info("Port-gate capability removed");
        portGateConfig.clear();
    }

    private void applyConfig(final CapabilityContext context) {
        Config cfg = createConfig(context.properties());
        portGateConfig.update(true, cfg.scopedPort(), cfg.allowedPaths());
    }

    /**
     * Parsed config properties. Handles defaults and type conversion.
     */
    static final class Config {
        private static final String PROP_SCOPED_PORT = "scopedPort";
        private static final String PROP_ALLOWED_PATHS = "allowedPaths";
        private static final int DEFAULT_SCOPED_PORT = 8443;

        private final int scopedPort;
        private final java.util.List<String> allowedPaths;

        Config(final Map<String, String> properties) {
            String portStr = properties.getOrDefault(PROP_SCOPED_PORT,
                String.valueOf(DEFAULT_SCOPED_PORT));
            this.scopedPort = parsePort(portStr);
            this.allowedPaths = PortGateConfig.parseAllowedPaths(
                properties.getOrDefault(PROP_ALLOWED_PATHS, ""));
        }

        int scopedPort() {
            return scopedPort;
        }

        java.util.List<String> allowedPaths() {
            return allowedPaths;
        }

        private static int parsePort(String value) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                log.warn("Invalid scopedPort '{}', using default {}", value, DEFAULT_SCOPED_PORT);
                return DEFAULT_SCOPED_PORT;
            }
        }
    }
}
