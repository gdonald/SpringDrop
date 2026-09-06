package dev.springdrop.kernel.user;

/**
 * A person's account as the rest of the site reads it: who they are, whether
 * they may sign in, and the settings that shape what they see.
 */
public record UserAccount(
        long id,
        String name,
        String mail,
        boolean active,
        String timezone,
        String preferredLanguage) {

    /** The account a request has when nobody has signed in. */
    public static final long ANONYMOUS_ID = 0L;

    /** The first account, which may do anything the site allows. */
    public static final long ADMINISTRATOR_ID = 1L;

    public static final String ANONYMOUS_NAME = "Anonymous";

    public boolean isAnonymous() {
        return id == ANONYMOUS_ID;
    }

    /** The first account is not subject to permission checks. */
    public boolean bypassesAccessChecks() {
        return id == ADMINISTRATOR_ID;
    }
}
