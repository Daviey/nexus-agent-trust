package com.example.nexus.portgate;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nexus port-gate filter: gates repository access based on the local TCP port
 * the request arrived on.
 *
 * On the scoped port (configured via the Capability UI), only paths matching
 * an allowlisted prefix are served. Everything else returns 403.
 *
 * When the capability is disabled or not yet configured, the scoped port
 * denies all repository access.
 *
 * Configuration is managed via the Nexus Capability UI:
 *   Administration > Capabilities > Agent Trust: Port Gate
 *
 * Changes take effect on the next request. No restart needed.
 */
@Named
@Singleton
public class PortGateFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(PortGateFilter.class);

    private final PortGateConfig config;

    @Inject
    public PortGateFilter(final PortGateConfig config) {
        this.config = config;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        log.info("Port-gate filter initialised");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        int localPort = request.getLocalPort();

        if (localPort == config.getScopedPort()) {
            if (!config.isEnabled()) {
                deny(response, localPort, "capability disabled");
                return;
            }

            HttpServletRequest httpRequest = (HttpServletRequest) request;
            String path = httpRequest.getRequestURI();

            List<String> allowed = config.getAllowedPrefixes();
            boolean matched = false;
            for (String prefix : allowed) {
                if (path.startsWith(prefix)) {
                    matched = true;
                    break;
                }
            }

            if (!matched) {
                deny(response, localPort, "path not in allowlist: " + path);
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private void deny(ServletResponse response, int port, String reason) throws IOException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setStatus(403);
        httpResponse.setContentType("text/plain");
        httpResponse.getWriter().write(
            "Port-gate: access denied on scoped port " + port + " (" + reason + ")\n");
    }

    @Override
    public void destroy() {
    }
}
