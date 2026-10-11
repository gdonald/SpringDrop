package dev.springdrop.kernel.action.plugins;

import dev.springdrop.kernel.user.AccountPrincipal;
import org.springframework.security.core.Authentication;

/** The account an action runs as. */
final class Accounts {

    private Accounts() {
    }

    /** The id of the signed-in account, or the anonymous account's for anyone else. */
    static long idOf(Authentication who) {
        return (who.getPrincipal() instanceof AccountPrincipal account) ? account.id() : 0L;
    }
}
