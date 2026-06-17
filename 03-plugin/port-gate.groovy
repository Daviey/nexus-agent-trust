// Nexus Port-Gate Groovy Script
//
// Uploaded via the Nexus scripting REST API. Runs inside the Nexus JVM
// with access to internal components. Dynamically adds a servlet filter
// to the Jetty server that gates repository access based on the local port.
//
// This stands in for the compiled Java plugin (see src/main/java/). In
// production you would build and deploy the OSGi bundle; for a PoC, the
// Groovy script proves the same concept without a build step.

import javax.servlet.*
import javax.servlet.http.*
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.FilterHolder
import org.slf4j.Logger

// ─── Define the filter inline ──────────────────────────────

class PortGateFilter implements Filter {
    int scopedPort
    List allowedPrefixes
    Logger log

    void init(FilterConfig fc) {
        log?.info("PortGateFilter initialised, scopedPort={}", scopedPort)
    }

    void destroy() {}

    void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {
        int port = req.getLocalPort()
        String path = ((HttpServletRequest) req).getRequestURI()

        log?.info("PortGate: port={} path={}", port, path)

        if (port == scopedPort) {
            boolean ok = false
            for (String prefix : allowedPrefixes) {
                if (path.startsWith(prefix.trim())) {
                    ok = true
                    break
                }
            }
            if (!ok) {
                log?.info("PortGate: DENY port={} path={}", port, path)
                ((HttpServletResponse) res).sendError(403,
                    "Port-gate: denied on scoped port ${port} for ${path}")
                return
            }
        }
        chain.doFilter(req, res)
    }
}

// ─── Try to register the filter with Jetty ─────────────────

def results = []

try {
    // Attempt 1: look up Jetty Server via the Plexus container
    def container = core.getContainer()
    def server = container.getComponent(org.eclipse.jetty.server.Server.class)

    if (server == null) {
        results << "Could not locate Jetty Server via container"
    } else {
        results << "Found Jetty Server: ${server.class.name}"

        // Walk the handler tree to find ServletContextHandler
        def found = false
        def walkHandlers
        walkHandlers = { handler ->
            if (handler == null) return
            if (handler instanceof ServletContextHandler) {
                results << "Found ServletContextHandler: ${handler.contextPath}"

                // Create and register the filter
                def filter = new PortGateFilter()
                filter.scopedPort = 8082
                filter.allowedPrefixes = ["/repository/trusted/"]
                filter.log = log

                def holder = new FilterHolder(filter)
                holder.name = "portGate"
                holder.setAsyncSupported(true)

                def mapping = handler.addFilter(
                    holder, "/*", java.util.EnumSet.of(
                        javax.servlet.DispatcherType.REQUEST))

                results << "Filter registered on context: ${handler.contextPath}"
                found = true
            }
            if (handler?.handlers != null) {
                for (def h : handler.handlers) {
                    walkHandlers(h)
                }
            }
        }
        walkHandlers(server.handler)

        if (!found) {
            results << "No ServletContextHandler found in handler tree"
            // List what we did find
            def listHandlers
            listHandlers = { h, depth ->
                if (h == null) return
                results << "${'  ' * depth}${h.class.name}"
                if (h.handlers != null) {
                    for (def child : h.handlers) {
                        listHandlers(child, depth + 1)
                    }
                }
            }
            listHandlers(server.handler, 0)
        }
    }
} catch (Exception e) {
    results << "ERROR: ${e.class.name}: ${e.message}"
    e.stackTrace?.take(10)?.each { results << "  at ${it}" }
}

return results.join("\n")
