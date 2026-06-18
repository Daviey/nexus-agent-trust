package com.sonatype.nexus.plugins.portgate;

import java.io.IOException;
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
import org.apache.shiro.subject.Subject;
import org.apache.shiro.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servlet filter that gates repository access on scoped ports.
 *
 * Checks in order:
 * 1. Is this a scoped port? If not, pass through.
 * 2. Is the path under /repository/? If not, pass through (API/UI unaffected).
 * 3. Read-only mode active and this is a write method? Deny.
 * 4. Path in the allowlist? Allow.
 * 5. Otherwise: deny (or audit-deny if audit mode is on).
 *
 * Every decision is logged with the authenticated user, client IP, HTTP
 * method, and request path.
 */
@Named
@Singleton
public class PortGateFilter implements Filter
{
    private static final Logger log = LoggerFactory.getLogger(PortGateFilter.class);

    private final PortGateConfig config;

    @Inject
    public PortGateFilter(PortGateConfig config) {
        this.config = config;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        log.info("Port-gate filter initialised");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        int localPort = request.getLocalPort();

        if (!config.isScopedPort(localPort)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        if (!path.startsWith("/repository/")) {
            chain.doFilter(request, response);
            return;
        }

        String method = httpRequest.getMethod();
        String user = getUser();
        String ip = getClientIp(request);

        boolean isWrite = "POST".equals(method) || "PUT".equals(method)
            || "DELETE".equals(method) || "PATCH".equals(method);

        if (config.isReadOnly(localPort) && isWrite) {
            if (config.isAuditMode(localPort)) {
                log.warn("Port-gate: AUDIT-DENY port={} user={} ip={} method={} path={} (write blocked by read-only mode)",
                    localPort, user, ip, method, path);
                chain.doFilter(request, response);
            } else {
                log.warn("Port-gate: DENY port={} user={} ip={} method={} path={} (write blocked by read-only mode)",
                    localPort, user, ip, method, path);
                HttpServletResponse hr = (HttpServletResponse) response;
                hr.setStatus(HttpServletResponse.SC_FORBIDDEN);
                hr.setContentType("text/plain");
                hr.getWriter().write("Port-gate: write operations blocked on port " + localPort + " (read-only mode)\n");
            }
            return;
        }

        if (config.isAllowed(localPort, path)) {
            log.info("Port-gate: ALLOW port={} user={} ip={} path={}", localPort, user, ip, path);
            chain.doFilter(request, response);
            return;
        }

        if (config.isAuditMode(localPort)) {
            log.warn("Port-gate: AUDIT-DENY port={} user={} ip={} path={} (would be blocked in enforce mode)",
                localPort, user, ip, path);
            chain.doFilter(request, response);
        } else {
            log.warn("Port-gate: DENY port={} user={} ip={} path={}", localPort, user, ip, path);
            HttpServletResponse hr = (HttpServletResponse) response;
            hr.setStatus(HttpServletResponse.SC_FORBIDDEN);
            hr.setContentType("text/plain");
            hr.getWriter().write("Port-gate: access denied on port " + localPort + " for " + path + "\n");
        }
    }

    private String getUser() {
        try {
            Subject subject = SecurityUtils.getSubject();
            return subject != null && subject.getPrincipal() != null
                ? subject.getPrincipal().toString() : "anonymous";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String getClientIp(ServletRequest request) {
        try {
            HttpServletRequest http = (HttpServletRequest) request;
            String forwarded = http.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return "unknown";
        }
    }

    @Override
    public void destroy() {
        log.info("Port-gate filter destroyed. Ports: {}", config.getPortSummary());
    }
}
