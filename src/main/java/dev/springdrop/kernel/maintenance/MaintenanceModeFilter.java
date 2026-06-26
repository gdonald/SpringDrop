package dev.springdrop.kernel.maintenance;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Serves a maintenance page while maintenance mode is enabled. Excluded paths
 * (the actuator and the error pipeline) stay reachable so health checks and
 * error rendering keep working, and a {@link MaintenanceBypass} can let
 * privileged requests through.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class MaintenanceModeFilter extends OncePerRequestFilter {

    private static final List<String> EXCLUDED_PREFIXES = List.of("/actuator", "/error");

    private final MaintenanceProperties properties;
    private final MaintenanceBypass bypass;
    private final ITemplateEngine templateEngine;

    public MaintenanceModeFilter(
            MaintenanceProperties properties,
            MaintenanceBypass bypass,
            ITemplateEngine templateEngine) {
        this.properties = properties;
        this.bypass = bypass;
        this.templateEngine = templateEngine;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if (isBlocked(request)) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("text/html;charset=UTF-8");
            Context context = new Context(request.getLocale(), Map.of("message", properties.message()));
            response.getWriter().write(templateEngine.process("maintenance", context));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isBlocked(HttpServletRequest request) {
        if (!properties.enabled()) {
            return false;
        }
        if (isExcluded(request.getRequestURI())) {
            return false;
        }
        return !bypass.isAllowed(request);
    }

    private boolean isExcluded(String uri) {
        return EXCLUDED_PREFIXES.stream().anyMatch(uri::startsWith);
    }
}
