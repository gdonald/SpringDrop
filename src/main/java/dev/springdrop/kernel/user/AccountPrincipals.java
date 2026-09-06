package dev.springdrop.kernel.user;

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
}
