package dev.springdrop.kernel.access;

import dev.springdrop.kernel.routing.RouteDefinition;
import dev.springdrop.kernel.routing.RouteRegistry;
import dev.springdrop.kernel.security.ActionLinkTokenService;
import dev.springdrop.kernel.user.AccountPrincipals;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Decides whether the current user may reach a request, and reports the
 * cacheability of that decision. A permission check varies by the caller's
 * permissions, so its result carries the {@code user.permissions} context; every
 * decision about a registered route carries that route's tag, so changing the
 * route invalidates decisions made about it. An action link route additionally
 * requires a valid signed token, and its decision is never cacheable because it
 * depends on the token in the URL.
 */
@Component
public class RouteAccessChecker {

    public static final String USER_PERMISSIONS_CONTEXT = "user.permissions";

    public static final String ROUTE_TAG_PREFIX = "route:";

    private final RouteRegistry routeRegistry;
    private final ActionLinkTokenService actionLinkTokenService;

    public RouteAccessChecker(RouteRegistry routeRegistry, ActionLinkTokenService actionLinkTokenService) {
        this.routeRegistry = routeRegistry;
        this.actionLinkTokenService = actionLinkTokenService;
    }

    public AccessResult check(HttpServletRequest request, Authentication authentication) {
        Optional<RouteDefinition> match = routeRegistry.match(request.getRequestURI());
        if (match.isEmpty()) {
            return AccessResult.allow();
        }

        if (AccountPrincipals.bypassesChecks(authentication)) {
            return AccessResult.allow().withCacheContext(USER_PERMISSIONS_CONTEXT);
        }

        RouteDefinition route = match.get();
        AccessResult result = permissionResult(route, authentication)
                .withCacheTag(ROUTE_TAG_PREFIX + route.name());
        if (!route.requiresLinkToken()) {
            return result;
        }
        return result.and(linkTokenResult(request, authentication));
    }

    private AccessResult permissionResult(RouteDefinition route, Authentication authentication) {
        String requiredPermission = route.requiredPermission();
        if (requiredPermission == null) {
            return AccessResult.allow();
        }

        boolean granted = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(requiredPermission));
        return (granted ? AccessResult.allow() : AccessResult.forbid())
                .withCacheContext(USER_PERMISSIONS_CONTEXT);
    }

    private AccessResult linkTokenResult(HttpServletRequest request, Authentication authentication) {
        boolean valid = actionLinkTokenService.isValid(
                request.getRequestURI(),
                request.getParameter(ActionLinkTokenService.TOKEN_PARAMETER),
                ActionLinkTokenService.identityOf(authentication));
        return (valid ? AccessResult.allow() : AccessResult.forbid()).uncacheable();
    }
}
