package dev.springdrop.kernel.security;

import dev.springdrop.kernel.routing.RouteDefinition;
import dev.springdrop.kernel.routing.RouteRegistry;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Authorizes a request against the matched route's required permission. A route
 * with no required permission is open; otherwise the current user must hold the
 * matching authority. Permissions map to Spring Security authorities until the
 * role and permission system replaces this with real permission checks.
 */
public class RouteAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final RouteRegistry routeRegistry;

    public RouteAuthorizationManager(RouteRegistry routeRegistry) {
        this.routeRegistry = routeRegistry;
    }

    @Override
    public AuthorizationResult authorize(
            Supplier<? extends Authentication> authentication,
            RequestAuthorizationContext context) {

        String requiredPermission = routeRegistry.match(context.getRequest().getRequestURI())
                .map(RouteDefinition::requiredPermission)
                .orElse(null);

        if (requiredPermission == null) {
            return new AuthorizationDecision(true);
        }

        boolean granted = authentication.get().getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(requiredPermission));
        return new AuthorizationDecision(granted);
    }
}
