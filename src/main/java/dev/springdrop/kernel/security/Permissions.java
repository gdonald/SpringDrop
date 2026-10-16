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

    /** Adding menus and deciding what hangs in them. */
    String ADMINISTER_MENU = "administer menu";

    /** Placing blocks in regions, and writing the custom blocks in the library. */
    String ADMINISTER_BLOCKS = "administer blocks";

    /** Adding workflows and deciding their states and transitions. */
    String ADMINISTER_WORKFLOWS = "administer workflows";

    /** Adding languages and deciding how a request's language is chosen. */
    String ADMINISTER_LANGUAGES = "administer languages";

    /** Translating the site's interface strings, and importing and exporting translations. */
    String TRANSLATE_INTERFACE = "translate interface";

    /** Adding, editing, and deleting translations of content the person may change. */
    String TRANSLATE_CONTENT = "translate content";

    /** Translating the names, help, and titles config objects show. */
    String TRANSLATE_CONFIGURATION = "translate configuration";

    /** Choosing which entity types are served over REST, by which methods, and in which formats. */
    String ADMINISTER_REST_RESOURCES = "administer rest resources";

    /** Running the update routines of core and the modules. */
    String ADMINISTER_SOFTWARE_UPDATES = "administer software updates";

    /** Enabling and disabling the site's modules. */
    String ADMINISTER_MODULES = "administer modules";

    /** Seeing the administration toolbar at the top of each page. */
    String ACCESS_TOOLBAR = "access toolbar";

    /** Opening the overview pages of the administration sections. */
    String ACCESS_ADMINISTRATION_PAGES = "access administration pages";

    /** Exporting the site's configuration to the sync directory and importing it from there. */
    String SYNCHRONIZE_CONFIGURATION = "synchronize configuration";

    /** Creating and revoking one's own personal access tokens for the web service endpoints. */
    String USE_API_TOKENS = "use api tokens";

    /** Reading the activity log and the status report. */
    String ACCESS_SITE_REPORTS = "access site reports";

    /** Giving a piece of content that may be edited a layout of its own. */
    String CONFIGURE_LAYOUT_OVERRIDES = "configure layout overrides";
}
