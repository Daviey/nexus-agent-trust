package com.example.nexus.portgate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe holder for the port-gate configuration.
 *
 * The Capability component calls {@link #update} when config changes in the
 * Nexus UI. The filter reads the current config on every request via the
 * getter methods. No locking on the hot path; the AtomicReference swap is
 * the only synchronisation point.
 *
 * When the capability is disabled or not yet created, {@link #enabled}
 * is false and {@link #scopedPort} is 0. The filter treats scopedPort=0 as
 * "not configured" and denies everything on port 0 (which never receives
 * traffic), effectively allowing all traffic through until configured.
 */
@Named
@Singleton
public class PortGateConfig {

    private static final Logger log = LoggerFactory.getLogger(PortGateConfig.class);

    private final AtomicReference<Snapshot> current = new AtomicReference<>(
        new Snapshot(false, 0, Collections.emptyList())
    );

    /**
     * Called by the capability when config changes or capability state
     * changes (enabled/disabled). Thread-safe.
     */
    void update(boolean enabled, int scopedPort, List<String> allowedPrefixes) {
        List<String> copy = Collections.unmodifiableList(new ArrayList<>(allowedPrefixes));
        current.set(new Snapshot(enabled, scopedPort, copy));
        log.info("Port-gate config updated: enabled={}, scopedPort={}, {} allowed path(s)",
            enabled, scopedPort, copy.size());
    }

    /**
     * Called when the capability is disabled or removed.
     */
    void clear() {
        current.set(new Snapshot(false, 0, Collections.emptyList()));
        log.info("Port-gate config cleared (capability disabled or removed)");
    }

    public boolean isEnabled() {
        return current.get().enabled;
    }

    public int getScopedPort() {
        return current.get().scopedPort;
    }

    public List<String> getAllowedPrefixes() {
        return current.get().allowedPrefixes;
    }

    /**
     * Parse the text-area content into a list of path prefixes.
     * One prefix per line. Lines starting with # are comments.
     * Blank lines are ignored. Leading/trailing whitespace is trimmed.
     */
    static List<String> parseAllowedPaths(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String line : raw.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            result.add(trimmed);
        }
        return result;
    }

    private static final class Snapshot {
        final boolean enabled;
        final int scopedPort;
        final List<String> allowedPrefixes;

        Snapshot(boolean enabled, int scopedPort, List<String> allowedPrefixes) {
            this.enabled = enabled;
            this.scopedPort = scopedPort;
            this.allowedPrefixes = allowedPrefixes;
        }
    }
}
