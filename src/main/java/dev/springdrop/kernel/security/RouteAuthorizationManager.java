package dev.springdrop.kernel.security;

import dev.springdrop.kernel.access.AccessResult;
import dev.springdrop.kernel.access.RouteAccessChecker;
import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Authorizes a request through the {@link RouteAccessChecker} and leaves the
 * decision, with its cache contexts and tags, on the request so the page cache
 * can vary on the same metadata the decision was made from.
 */
public class RouteAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    public static final String ACCESS_RESULT_ATTRIBUTE = AccessResult.class.getName();

    private final RouteAccessChecker accessChecker;

    public RouteAuthorizationManager(RouteAccessChecker accessChecker) {
        this.accessChecker = accessChecker;
    }

    @Override
    public AuthorizationResult authorize(
            Supplier<? extends Authentication> authentication,
            RequestAuthorizationContext context) {

        AccessResult result = accessChecker.check(context.getRequest(), authentication.get());
        context.getRequest().setAttribute(ACCESS_RESULT_ATTRIBUTE, result);
        return new AuthorizationDecision(result.allowed());
    }
}
