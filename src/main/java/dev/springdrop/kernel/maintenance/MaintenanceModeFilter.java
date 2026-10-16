package dev.springdrop.kernel.maintenance;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import dev.springdrop.kernel.security.SecurityConfig;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Serves a maintenance page while maintenance mode is enabled. Excluded paths
 * (the actuator, the error pipeline, and signing in and out) stay reachable so
 * health checks and error rendering keep working and an administrator can
 * sign in, and a {@link MaintenanceBypass} can let privileged requests
 * through. It runs after the security chain, so it knows who is asking.
 */
@Component
@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 10)
public class MaintenanceModeFilter extends OncePerRequestFilter {

    private static final List<String> EXCLUDED_PREFIXES = List.of("/actuator", "/error", SecurityConfig.LOGIN_PATH,
            SecurityConfig.LOGOUT_PATH);

    private final Maintenance maintenance;
    private final MaintenanceBypass bypass;
    private final ITemplateEngine templateEngine;

    public MaintenanceModeFilter(
            Maintenance maintenance,
            MaintenanceBypass bypass,
            ITemplateEngine templateEngine) {
        this.maintenance = maintenance;
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
            Context context = new Context(request.getLocale(), Map.of("message", maintenance.settings().message()));
            response.getWriter().write(templateEngine.process("maintenance", context));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isBlocked(HttpServletRequest request) {
        if (!maintenance.settings().enabled()) {
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
