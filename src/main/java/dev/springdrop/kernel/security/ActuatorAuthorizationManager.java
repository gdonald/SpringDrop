package dev.springdrop.kernel.security;

import java.util.function.Supplier;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Authorizes the actuator health and info endpoints. In production they expose
 * component health and build detail, so they require a user holding the site
 * administration permission. Elsewhere they stay open, which keeps the dev loop
 * and the container healthcheck free of credentials.
 */
public class ActuatorAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final boolean administratorRequired;

    public ActuatorAuthorizationManager(boolean administratorRequired) {
        this.administratorRequired = administratorRequired;
    }

    @Override
    public AuthorizationResult authorize(
            Supplier<? extends Authentication> authentication,
            RequestAuthorizationContext context) {

        if (!administratorRequired) {
            return new AuthorizationDecision(true);
        }

        boolean granted = authentication.get().getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(Permissions.ADMINISTER_SITE_CONFIGURATION));
        return new AuthorizationDecision(granted);
    }
}
