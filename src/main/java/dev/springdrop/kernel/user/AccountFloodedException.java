package dev.springdrop.kernel.user;

import org.springframework.security.authentication.AccountStatusException;

/**
 * Raised when an account has been guessed at too often lately. The sign-in is
 * refused without the password being looked at, so guessing costs the same
 * whether or not the password was close.
 */
public class AccountFloodedException extends AccountStatusException {

    private static final long serialVersionUID = 1L;

    public AccountFloodedException(String message) {
        super(message);
    }
}
