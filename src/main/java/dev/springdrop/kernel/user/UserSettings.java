package dev.springdrop.kernel.user;

/**
 * How a site handles people signing up: whether they may at all, whether an
 * administrator has to approve them first, and whether they must prove they own
 * the address they gave. Stored as the {@code user.settings} config object.
 */
public record UserSettings(String registrationMode, boolean requireEmailVerification) {

    /** Anyone may sign up and use the account at once. */
    public static final String OPEN = "open";

    /** Anyone may sign up, but an administrator lets them in. */
    public static final String ADMIN_APPROVAL = "admin_approval";

    /** Only an administrator creates accounts. */
    public static final String ADMIN_ONLY = "admin_only";

    public static final String CONFIG_NAME = "user.settings";

    public static final UserSettings DEFAULTS = new UserSettings(ADMIN_APPROVAL, false);

    public boolean allowsSelfRegistration() {
        return !ADMIN_ONLY.equals(registrationMode);
    }

    public boolean needsApproval() {
        return ADMIN_APPROVAL.equals(registrationMode);
    }
}
