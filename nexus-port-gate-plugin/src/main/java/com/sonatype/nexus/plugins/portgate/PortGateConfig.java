package com.sonatype.nexus.plugins.portgate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Named;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Multi-port configuration holder.
 *
 * Each scoped port has its own allowlist, audit flag, and read-only flag.
 * Multiple capability instances can coexist, each managing a different port.
 *
 * Thread-safe via {@link ConcurrentHashMap}. The filter reads config on
 * every request via the getter methods without locking.
 */
@Named
@Singleton
public class PortGateConfig
{
    private static final Logger log = LoggerFactory.getLogger(PortGateConfig.class);

    private final ConcurrentHashMap<Integer, PortEntry> ports = new ConcurrentHashMap<>();

    static final class PortEntry
    {
        final List<String> allowedPrefixes;
        final boolean auditMode;
        final boolean readOnly;

        PortEntry(List<String> prefixes, boolean auditMode, boolean readOnly) {
            this.allowedPrefixes = List.copyOf(prefixes);
            this.auditMode = auditMode;
            this.readOnly = readOnly;
        }
    }

    void update(int scopedPort, List<String> prefixes, boolean auditMode, boolean readOnly) {
        ports.put(scopedPort, new PortEntry(prefixes, auditMode, readOnly));
        log.info("Port-gate: port={} | {} prefix(es) | audit={} | readOnly={}",
            scopedPort, prefixes.size(), auditMode, readOnly);
    }

    void remove(int scopedPort) {
        ports.remove(scopedPort);
        log.info("Port-gate: deregistered port {}", scopedPort);
    }

    public boolean isScopedPort(int port) {
        return ports.containsKey(port);
    }

    public boolean isAuditMode(int port) {
        PortEntry entry = ports.get(port);
        return entry != null && entry.auditMode;
    }

    public boolean isReadOnly(int port) {
        PortEntry entry = ports.get(port);
        return entry != null && entry.readOnly;
    }

    public boolean isAllowed(int port, String path) {
        PortEntry entry = ports.get(port);
        if (entry == null) return true;
        for (String prefix : entry.allowedPrefixes) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }

    public Map<Integer, String> getPortSummary() {
        Map<Integer, String> summary = new TreeMap<>();
        ports.forEach((port, entry) ->
            summary.put(port, entry.allowedPrefixes.size() + " repos, audit=" + entry.auditMode));
        return summary;
    }

    static List<String> parseRepos(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> prefixes = new ArrayList<>();
        for (String name : raw.split(",")) {
            String t = name.trim();
            if (!t.isEmpty()) prefixes.add("/repository/" + t + "/");
        }
        return prefixes;
    }
}
