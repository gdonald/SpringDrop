package dev.springdrop.kernel.menu;

import dev.springdrop.kernel.user.AccountPrincipals;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Whether the person making the current request holds a permission. The tree
 * builder asks this rather than reading the security context itself, so the
 * first account's bypass is honored in menus as it is everywhere else.
 */
@Component
public class MenuPermissionChecker {

    public boolean holds(String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (AccountPrincipals.bypassesChecks(authentication)) {
            return true;
        }
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(permission));
    }
}
