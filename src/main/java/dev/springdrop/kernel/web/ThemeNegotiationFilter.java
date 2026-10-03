package dev.springdrop.kernel.web;

import dev.springdrop.kernel.routing.ThemeResolver;
import dev.springdrop.kernel.theme.ThemeRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Activates the theme the route calls for, so templates resolve against the admin
 * theme on admin routes and the front-end theme everywhere else. The theme is
 * dropped once the request is done, leaving the thread as it was found.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class ThemeNegotiationFilter extends OncePerRequestFilter {

    /**
     * The request attribute a filter that answers a path as another sets to the
     * path the reader asked for, which the theme follows.
     */
    public static final String REQUESTED_PATH = ThemeNegotiationFilter.class.getName() + ".requestedPath";

    private final ThemeResolver themeResolver;
    private final ThemeRegistry registry;

    public ThemeNegotiationFilter(ThemeResolver themeResolver, ThemeRegistry registry) {
        this.themeResolver = themeResolver;
        this.registry = registry;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Object requested = request.getAttribute(REQUESTED_PATH);
        registry.activate(themeResolver.resolve(requested instanceof String path ? path : request.getRequestURI()));
        try {
            filterChain.doFilter(request, response);
        } finally {
            registry.deactivate();
        }
    }
}
