package dev.springdrop.kernel.maintenance;

import dev.springdrop.kernel.user.AccountPrincipals;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Lets through the first account and anyone holding the permission to use the site in maintenance mode. */
@Component
@Primary
public class PermissionMaintenanceBypass implements MaintenanceBypass {

    public static final String ACCESS_SITE_IN_MAINTENANCE_MODE = "access site in maintenance mode";

    @Override
    public boolean isAllowed(HttpServletRequest request) {
        Authentication who = SecurityContextHolder.getContext().getAuthentication();
        return AccountPrincipals.bypassesChecks(who) || (who != null && who.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(ACCESS_SITE_IN_MAINTENANCE_MODE)));
    }
}
