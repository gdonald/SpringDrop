package dev.springdrop.kernel.user;

/**
 * Raised when an account would take a name or an email address another account
 * already holds, whatever case it was typed in.
 */
public class DuplicateAccountException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicateAccountException(String message) {
        super(message);
    }
}
