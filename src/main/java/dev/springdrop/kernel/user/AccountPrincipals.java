package dev.springdrop.kernel.user;

import dev.springdrop.kernel.role.RoleConfig;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Reading the account behind a request. The first account is not subject to
 * permission or access checks, and every part of the site that honors that asks
 * here, so there is one answer rather than three.
 */
public interface AccountPrincipals {

    static boolean bypassesChecks(Authentication authentication) {
        return authentication != null
                && authentication.getPrincipal() instanceof AccountPrincipal account
                && account.bypassesAccessChecks();
    }

    /**
     * The roles behind a request. Someone who has not signed in holds the
     * anonymous role alone. Someone who has holds the authenticated role and
     * whatever roles their authorities name.
     */
    static Set<String> rolesOf(Authentication authentication) {
        Set<String> roles = new LinkedHashSet<>();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            roles.add(RoleConfig.ANONYMOUS);
            return roles;
        }
        roles.add(RoleConfig.AUTHENTICATED);
        authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .filter(authority -> authority.startsWith(AccountPrincipal.ROLE_PREFIX))
                .forEach(authority -> roles.add(authority.substring(AccountPrincipal.ROLE_PREFIX.length())));
        return roles;
    }
}
