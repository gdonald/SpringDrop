package dev.springdrop.kernel.web;

import dev.springdrop.kernel.routing.ThemeResolver;
import dev.springdrop.kernel.theme.ThemeRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Activates the theme the route calls for, so templates resolve against the admin
 * theme on admin routes and the front-end theme everywhere else. The theme is
 * dropped once the request is done, leaving the thread as it was found.
 */
@Component
public class ThemeNegotiationFilter extends OncePerRequestFilter {

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

        registry.activate(themeResolver.resolve(request.getRequestURI()));
        try {
            filterChain.doFilter(request, response);
        } finally {
            registry.deactivate();
        }
    }
}
