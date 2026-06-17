package com.example.nexus.portgate;

import java.io.IOException;
import java.util.List;
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

/**
 * Nexus port-gate filter: gates repository access based on the local port
 * the request arrived on. Deploy this as an OSGi bundle in Nexus's deploy/
 * directory.
 *
 * On the scoped port (default 8082), only paths starting with an allowlisted
 * prefix are served. Everything else returns 403.
 *
 * The allowlist is read from a system property:
 *   -Dnexus.portgate.allowed=/repository/trusted/,/repository/npm-agent/
 *
 * The scoped port is configurable:
 *   -Dnexus.portgate.scopedPort=8082
 */
@Named
@Singleton
public class PortGateFilter implements Filter {

    private int scopedPort;
    private List<String> allowedPrefixes;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        scopedPort = Integer.parseInt(
            System.getProperty("nexus.portgate.scopedPort", "8082"));
        String allowed = System.getProperty(
            "nexus.portgate.allowed",
            "/repository/trusted/");
        allowedPrefixes = List.of(allowed.split(","));
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        int localPort = request.getLocalPort();

        if (localPort == scopedPort) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            String path = httpRequest.getRequestURI();

            boolean allowed = false;
            for (String prefix : allowedPrefixes) {
                if (path.startsWith(prefix.trim())) {
                    allowed = true;
                    break;
                }
            }

            if (!allowed) {
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.setStatus(403);
                httpResponse.setContentType("text/plain");
                httpResponse.getWriter().write(
                    "Port-gate: access denied on scoped port " + localPort
                    + " for path " + path + "\n");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
    }
}
