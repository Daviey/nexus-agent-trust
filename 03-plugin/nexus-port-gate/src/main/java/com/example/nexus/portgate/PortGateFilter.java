package com.example.nexus.portgate;

import java.io.IOException;
import java.util.List;
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
    public void init(FilterConfig filterConfig) {
        log.info("Port-gate filter initialised");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        int localPort = request.getLocalPort();
        if (localPort == config.getScopedPort() && config.isEnabled()) {
            String path = ((HttpServletRequest) request).getRequestURI();
            for (String prefix : config.getAllowedPrefixes()) {
                if (path.startsWith(prefix)) {
                    chain.doFilter(request, response);
                    return;
                }
            }
            HttpServletResponse hr = (HttpServletResponse) response;
            hr.setStatus(403);
            hr.setContentType("text/plain");
            hr.getWriter().write("Port-gate: denied on port " + localPort + " for " + path + "\n");
            return;
        }
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {}
}
