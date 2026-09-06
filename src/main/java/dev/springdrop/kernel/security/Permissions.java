package dev.springdrop.kernel.security;

/**
 * The permission names core checks. Modules add their own; these are the ones
 * the kernel itself enforces.
 */
public interface Permissions {

    String ADMINISTER_SITE_CONFIGURATION = "administer site configuration";

    /** Adding, changing, and removing fields, and laying out forms and displays. */
    String ADMINISTER_FIELDS = "administer fields";

    /** Deciding what each role may do. */
    String ADMINISTER_PERMISSIONS = "administer permissions";

    /** Managing accounts: their roles, and closing them. */
    String ADMINISTER_USERS = "administer users";

    /** Refusing requests from an address before the site answers them. */
    String BAN_IP_ADDRESSES = "ban ip addresses";
}
