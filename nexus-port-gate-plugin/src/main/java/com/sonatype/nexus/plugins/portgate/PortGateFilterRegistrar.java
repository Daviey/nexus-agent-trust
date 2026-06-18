package com.sonatype.nexus.plugins.portgate;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.DispatcherType;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers PortGateFilter AND opens the second Jetty connector.
 *
 * Spring Boot's FilterRegistrationBean uses jakarta.servlet which Nexus
 * doesn't have. And Nexus overrides the default web server factory so
 * WebServerFactoryCustomizer beans are never applied.
 *
 * This listener solves both problems:
 * 1. Registers the filter via {@link ServletContext#addFilter} (javax.servlet)
 * 2. Opens a second Jetty port via reflection on the running Server
 *
 * The Jetty Server is found by walking the ServletContext's class hierarchy
 * to find the {@code this$0} inner-class reference back to the
 * {@code WebAppContext}, which exposes {@code getServer()}.
 */
@Named
@Singleton
public class PortGateFilterRegistrar implements ServletContextListener
{
    private static final Logger log = LoggerFactory.getLogger(PortGateFilterRegistrar.class);
    private static final int DEFAULT_SCOPED_PORT = 8443;

    private final PortGateFilter filter;

    @Inject
    public PortGateFilterRegistrar(PortGateFilter filter) {
        this.filter = filter;
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext ctx = sce.getServletContext();

        registerFilter(ctx);
        openScopedPort(ctx);
    }

    private void registerFilter(ServletContext ctx) {
        try {
            ctx.addFilter("port-gate-filter", filter)
                .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, "/repository/*");
            log.info("Port-gate filter registered for /repository/*");
        } catch (Exception e) {
            log.error("Port-gate: failed to register filter", e);
        }
    }

    private void openScopedPort(ServletContext ctx) {
        int port = Integer.getInteger("portgate.port", DEFAULT_SCOPED_PORT);
        try {
            Server server = findJettyServer(ctx);
            if (server == null) {
                log.warn("Port-gate: could not find Jetty Server, port {} not opened", port);
                return;
            }
            ServerConnector connector = new ServerConnector(server);
            connector.setHost("0.0.0.0");
            connector.setPort(port);
            connector.setName("port-gate-" + port);
            server.addConnector(connector);
            connector.start();
            log.info("Port-gate: opened Jetty connector on port {}", port);
        } catch (Exception e) {
            log.error("Port-gate: failed to open connector on port {}", port, e);
        }
    }

    /**
     * Find the Jetty Server from the ServletContext via reflection.
     *
     * Jetty's ServletContext is WebAppContext.Context (an inner class).
     * Its {@code this$0} field points to the WebAppContext, which has
     * {@code getServer()}.
     */
    private Server findJettyServer(ServletContext ctx) {
        try {
            Class<?> cls = ctx.getClass();
            while (cls != null) {
                for (Field field : cls.getDeclaredFields()) {
                    if (field.getName().equals("this$0")) {
                        field.setAccessible(true);
                        Object handler = field.get(ctx);
                        if (handler != null) {
                            Method getServer = handler.getClass().getMethod("getServer");
                            Object server = getServer.invoke(handler);
                            if (server instanceof Server) {
                                log.info("Port-gate: found Jetty Server via {}", cls.getName());
                                return (Server) server;
                            }
                        }
                    }
                }
                cls = cls.getSuperclass();
            }
        } catch (Exception e) {
            log.warn("Port-gate: reflection lookup for Jetty Server failed", e);
        }
        return null;
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        log.info("Port-gate filter unregistered");
    }
}
