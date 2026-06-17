package com.example.nexus.portgate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named
@Singleton
public class PortGateConfig {

    private static final Logger log = LoggerFactory.getLogger(PortGateConfig.class);

    private final AtomicReference<Snapshot> current = new AtomicReference<>(
        new Snapshot(false, 0, Collections.emptyList())
    );

    void update(boolean enabled, int scopedPort, List<String> allowedPrefixes) {
        List<String> copy = Collections.unmodifiableList(new ArrayList<>(allowedPrefixes));
        current.set(new Snapshot(enabled, scopedPort, copy));
        log.info("Port-gate config updated: enabled={}, scopedPort={}, {} allowed path(s)",
            enabled, scopedPort, copy.size());
    }

    void clear() {
        current.set(new Snapshot(false, 0, Collections.emptyList()));
        log.info("Port-gate config cleared");
    }

    public boolean isEnabled() { return current.get().enabled; }
    public int getScopedPort() { return current.get().scopedPort; }
    public List<String> getAllowedPrefixes() { return current.get().allowedPrefixes; }

    static List<String> parseAllowedPaths(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (String line : raw.split("\n")) {
            String t = line.trim();
            if (!t.isEmpty() && !t.startsWith("#")) result.add(t);
        }
        return result;
    }

    private static final class Snapshot {
        final boolean enabled;
        final int scopedPort;
        final List<String> allowedPrefixes;
        Snapshot(boolean e, int p, List<String> a) { enabled = e; scopedPort = p; allowedPrefixes = a; }
    }
}
