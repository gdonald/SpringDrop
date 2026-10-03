package dev.springdrop.web;

import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccount;
import org.springframework.security.core.context.SecurityContextHolder;

/** The account making the request. The security chain identifies every request, signed in or not. */
interface CurrentAccount {

    static long id() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return (principal instanceof AccountPrincipal account) ? account.id() : UserAccount.ANONYMOUS_ID;
    }
}
