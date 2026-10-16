package dev.springdrop.web;

import dev.springdrop.kernel.menu.MenuConfig;
import dev.springdrop.kernel.menu.MenuLink;
import dev.springdrop.kernel.menu.MenuLinkProvider;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.security.SecurityConfig;
import dev.springdrop.kernel.views.DefaultViews;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The menu links core itself contributes, declared in code the way a module
 * declares its own. The Administration menu has a link for each section,
 * Content, Structure, Configuration, People, and Reports, with the section's
 * pages under it. A link is left out for someone who could not open its page.
 */
@Component
public class CoreMenuLinks implements MenuLinkProvider {

    public static final String HOME = "system.front_page";

    public static final String CONTENT = "system.admin_content";

    public static final String STRUCTURE = "system.admin_structure";

    public static final String CONFIGURATION = "system.admin_config";

    public static final String EXTEND = "system.modules_list";

    public static final String PEOPLE = "system.admin_people";

    public static final String REPORTS = "system.admin_reports";

    @Override
    public List<MenuLink> menuLinks() {
        List<MenuLink> links = new ArrayList<>(List.of(
                MenuLink.of(HOME, MenuConfig.MAIN, "Home", "/"),
                MenuLink.of(CONTENT, MenuConfig.ADMIN, "Content", "/admin/content").withWeight(-10)
                        .withDescription("Find and manage content.").requiring(DefaultViews.ACCESS_CONTENT_OVERVIEW),
                MenuLink.of(STRUCTURE, MenuConfig.ADMIN, "Structure", AdminSectionController.STRUCTURE_PATH)
                        .withWeight(-8).withDescription("Content types, menus, blocks, taxonomy, and views.")
                        .expandedByDefault(),
                MenuLink.of(CONFIGURATION, MenuConfig.ADMIN, "Configuration",
                                AdminSectionController.CONFIGURATION_PATH)
                        .withWeight(-6).withDescription("The settings of the site and its services.")
                        .expandedByDefault(),
                MenuLink.of(EXTEND, MenuConfig.ADMIN, "Extend", ModulesController.PATH).withWeight(-5)
                        .withDescription("Enable and disable modules."),
                MenuLink.of(PEOPLE, MenuConfig.ADMIN, "People", PeopleController.PATH).withWeight(-4)
                        .withDescription("The accounts on this site."),
                MenuLink.of(REPORTS, MenuConfig.ADMIN, "Reports", AdminSectionController.REPORTS_PATH)
                        .withWeight(-2).withDescription("The site's log and status.").expandedByDefault(),
                MenuLink.of("system.admin_permissions", MenuConfig.ADMIN, "Permissions",
                                RolePermissionsController.PATH)
                        .under(PEOPLE).withWeight(-5).withDescription("What each role may do."),
                MenuLink.of("user.login", MenuConfig.ACCOUNT, "Sign in", SecurityConfig.LOGIN_PATH),
                MenuLink.of("user.register", MenuConfig.ACCOUNT, "Create an account",
                                AccountController.REGISTER_PATH)
                        .withWeight(5),
                MenuLink.of("user.logout", MenuConfig.ACCOUNT, "Sign out", SecurityConfig.LOGOUT_PATH)
                        .withWeight(10)));
        section(links, CONTENT, List.of(
                page("comment.admin", "Comments", CommentAdminController.PATH, "Approve and remove comments."),
                page("media.admin", "Media", MediaController.OVERVIEW_PATH, "The images, documents, and videos kept."),
                page("block_content.library", "Custom block library", CustomBlockController.PATH,
                        "Blocks written once and placed wherever they are wanted.")));
        section(links, STRUCTURE, List.of(
                page("block.admin_display", "Block layout", BlockLayoutController.PATH,
                        "The blocks each region of each theme shows."),
                page("block_content.types", "Block types", BlockContentTypeController.PATH,
                        "The kinds of custom block, and the fields each carries."),
                page("contact.form_list", "Contact forms", ContactFormAdminController.PATH,
                        "The forms visitors send messages through."),
                page("node.types", "Content types", NodeTypeController.PATH,
                        "The kinds of content, and the fields each carries."),
                page("media.types", "Media types", MediaTypeController.PATH, "The kinds of media the site keeps."),
                MenuLink.of("system.admin_menus", MenuConfig.ADMIN, "Menus", MenuController.PATH)
                        .withDescription("The menus this site navigates by.").requiring(Permissions.ADMINISTER_MENU),
                page("taxonomy.vocabularies", "Taxonomy", TaxonomyController.PATH,
                        "The vocabularies content is tagged and sorted with."),
                page("views.list", "Views", ViewsUiController.PATH, "The listings of content and other entities.")));
        section(links, CONFIGURATION, List.of(
                page("system.site_information", "Basic site settings", SiteInformationController.PATH,
                        "The site's name, email address, front page, and error pages."),
                page("system.regional_settings", "Regional settings", RegionalSettingsController.PATH,
                        "The site's country, first day of the week, and timezone."),
                page("language.admin", "Languages", LanguageController.PATH, "The languages the site is in."),
                page("locale.translate", "User interface translation", TranslationController.PATH,
                        "Translations of the site's interface."),
                page("config_translation.admin", "Configuration translation", ConfigTranslationController.PATH,
                        "Translations of names, labels, and help."),
                page("filter.formats", "Text formats", TextFormatController.PATH,
                        "The formats text is written in and how each is filtered."),
                page("image.styles", "Image styles", ImageStyleController.PATH,
                        "The sizes and effects images are shown with."),
                page("responsive_image.styles", "Responsive image styles", ResponsiveImageStyleController.PATH,
                        "Images that fit the screen they are shown on."),
                page("system.file_system", "File system", FileSystemController.PATH,
                        "Where files are kept and how they are served."),
                page("search.settings", "Search settings", SearchSettingsController.PATH,
                        "The backend the site searches through."),
                page("path.patterns", "URL alias patterns", AliasPatternController.PATH,
                        "The addresses content is given as it is saved."),
                page("redirect.list", "Redirects", RedirectController.PATH, "Old addresses and where they lead now."),
                page("rest.resources", "REST resources", RestResourceController.PATH,
                        "Which content is served over REST."),
                page("jsonapi.settings", "JSON:API", JsonApiSettingsController.PATH,
                        "Whether JSON:API accepts writes."),
                page("system.rss", "RSS publishing", RssSettingsController.PATH,
                        "How many items each feed holds and how feeds describe the site."),
                page("system.performance", "Performance", PerformanceController.PATH,
                        "Caching, script aggregation, and automated cron."),
                page("system.logging", "Logging and errors", LoggingSettingsController.PATH,
                        "What error pages show and how many log messages are kept."),
                page("system.maintenance", "Maintenance mode", MaintenanceController.PATH,
                        "Take the site offline while it is worked on."),
                page("config.sync", "Configuration synchronization", ConfigSyncController.PATH,
                        "Export the site's configuration and import it from the sync directory."),
                page("system.cron", "Cron", CronController.PATH, "Housekeeping the site runs now and then."),
                page("system.actions", "Actions", ActionAdminController.PATH,
                        "The tasks bulk operations and other features run."),
                page("workflows.list", "Workflows", WorkflowController.PATH, "The states content moves through."),
                page("system.admin_ban", "Banned addresses", IpBanController.PATH,
                        "Addresses the site refuses.")));
        section(links, REPORTS, List.of(
                page("dblog.overview", "Recent log messages", ActivityLogController.PATH,
                        "Events the site has logged, newest first."),
                page("system.status", "Status report", StatusReportController.PATH,
                        "Whether the site has what it needs to run.")));
        return List.copyOf(links);
    }

    private static MenuLink page(String id, String title, String url, String description) {
        return MenuLink.of(id, MenuConfig.ADMIN, title, url).withDescription(description);
    }

    /** Adds the pages under a section, weighted in the order given. */
    private static void section(List<MenuLink> links, String section, List<MenuLink> pages) {
        for (int place = 0; place < pages.size(); place++) {
            links.add(pages.get(place).under(section).withWeight(place));
        }
    }
}
