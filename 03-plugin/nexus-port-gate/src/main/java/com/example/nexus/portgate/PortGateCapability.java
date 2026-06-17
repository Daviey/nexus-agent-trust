package com.example.nexus.portgate;

import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.nexus.capability.CapabilitySupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named(PortGateCapability.TYPE_ID)
@Singleton
public class PortGateCapability extends CapabilitySupport<PortGateCapability.Config> {

    public static final String TYPE_ID = "agent-trust.port-gate";
    private static final Logger log = LoggerFactory.getLogger(PortGateCapability.class);
    private static final int DEFAULT_PORT = 8443;

    private final PortGateConfig portGateConfig;

    @Inject
    public PortGateCapability(final PortGateConfig portGateConfig) {
        this.portGateConfig = portGateConfig;
    }

    @Override
    protected Config createConfig(final Map<String, String> properties) throws Exception {
        String portStr = properties.getOrDefault("scopedPort", String.valueOf(DEFAULT_PORT));
        int scopedPort;
        try { scopedPort = Integer.parseInt(portStr.trim()); }
        catch (NumberFormatException e) { scopedPort = DEFAULT_PORT; }
        List<String> allowed = PortGateConfig.parseAllowedPaths(
            properties.getOrDefault("allowedPaths", ""));
        return new Config(scopedPort, allowed);
    }

    @Override
    protected void onCreate(final Config config) throws Exception {
        log.info("Port-gate created");
    }

    @Override
    protected void onUpdate(final Config config) throws Exception {
        portGateConfig.update(true, config.scopedPort, config.allowedPaths);
        log.info("Port-gate updated: port={}, {} paths", config.scopedPort, config.allowedPaths.size());
    }

    @Override
    protected void onActivate(final Config config) throws Exception {
        portGateConfig.update(true, config.scopedPort, config.allowedPaths);
        log.info("Port-gate activated: port={}, {} paths", config.scopedPort, config.allowedPaths.size());
    }

    @Override
    protected void onPassivate(final Config config) throws Exception {
        portGateConfig.clear();
        log.info("Port-gate passivated");
    }

    @Override
    protected void onRemove(final Config config) throws Exception {
        portGateConfig.clear();
        log.info("Port-gate removed");
    }

    @Override
    protected String renderDescription() {
        Config cfg = getConfig();
        if (cfg != null) {
            return "Port-Gate: scoped port " + cfg.scopedPort
                + ", " + cfg.allowedPaths.size() + " allowed path(s)";
        }
        return "Port-Gate (not configured)";
    }

    static final class Config {
        final int scopedPort;
        final List<String> allowedPaths;
        Config(int scopedPort, List<String> allowedPaths) {
            this.scopedPort = scopedPort;
            this.allowedPaths = allowedPaths;
        }
    }
}
