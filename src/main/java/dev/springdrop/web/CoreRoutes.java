package dev.springdrop.web;

import dev.springdrop.kernel.comment.CommentPermissions;
import dev.springdrop.kernel.contact.ContactPermissions;
import dev.springdrop.kernel.filter.TextFormatManager;
import dev.springdrop.kernel.image.ImageStyleManager;
import dev.springdrop.kernel.media.MediaPermissions;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.routing.RouteDefinition;
import dev.springdrop.kernel.routing.RouteRegistrar;
import dev.springdrop.kernel.search.SearchPageManager;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.taxonomy.TaxonomyPermissions;
import dev.springdrop.kernel.theme.BigPipe;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The routes core itself contributes, registered the same way a module registers
 * its own.
 */
@Component
public class CoreRoutes implements RouteRegistrar {

    @Override
    public List<RouteDefinition> routes() {
        return List.of(
                RouteDefinition.frontEnd("/", "front_page", "Home"),
                RouteDefinition.frontEnd("/node/**", "node", "Content"),
                RouteDefinition.frontEnd("/taxonomy/**", "taxonomy_term", "Term"),
                RouteDefinition.frontEnd("/comment/**", "comment", "Comment"),
                RouteDefinition.admin(
                        TextFormatController.PATH + "/**",
                        "text_format_actions",
                        "Text formats",
                        TextFormatManager.ADMINISTER_FILTERS),
                RouteDefinition.admin(
                        TextFormatController.PATH,
                        "text_formats",
                        "Text formats",
                        TextFormatManager.ADMINISTER_FILTERS),
                RouteDefinition.admin(
                        ImageStyleController.PATH + "/**",
                        "image_style_actions",
                        "Image styles",
                        ImageStyleManager.ADMINISTER_IMAGE_STYLES),
                RouteDefinition.admin(
                        ImageStyleController.PATH,
                        "image_styles",
                        "Image styles",
                        ImageStyleManager.ADMINISTER_IMAGE_STYLES),
                RouteDefinition.admin(
                        ResponsiveImageStyleController.PATH + "/**",
                        "responsive_image_style_actions",
                        "Responsive image styles",
                        ResponsiveImageStyleController.ADMINISTER_RESPONSIVE_IMAGES),
                RouteDefinition.admin(
                        ResponsiveImageStyleController.PATH,
                        "responsive_image_styles",
                        "Responsive image styles",
                        ResponsiveImageStyleController.ADMINISTER_RESPONSIVE_IMAGES),
                RouteDefinition.admin(MediaTypeController.PATH, "media_types", "Media types",
                        MediaPermissions.ADMINISTER_MEDIA_TYPES),
                RouteDefinition.admin(MediaTypeController.PATH + "/add", "media_type_add", "Add media type",
                        MediaPermissions.ADMINISTER_MEDIA_TYPES),
                RouteDefinition.admin(MediaTypeController.PATH + "/manage/**", "media_type_actions", "Media types",
                        MediaPermissions.ADMINISTER_MEDIA_TYPES),
                RouteDefinition.admin(MediaController.OVERVIEW_PATH, "media_overview", "Media",
                        MediaPermissions.ACCESS_MEDIA_OVERVIEW),
                RouteDefinition.frontEnd("/media/**", "media", "Media"),
                RouteDefinition.admin(AliasPatternController.PATH, "alias_patterns", "URL alias patterns",
                        AliasPatternController.ADMINISTER_URL_ALIASES),
                RouteDefinition.admin(AliasPatternController.PATH + "/**", "alias_pattern_actions",
                        "URL alias patterns", AliasPatternController.ADMINISTER_URL_ALIASES),
                RouteDefinition.admin(RedirectController.PATH, "redirects", "Redirects",
                        RedirectController.ADMINISTER_REDIRECTS),
                RouteDefinition.admin(RedirectController.PATH + "/**", "redirect_actions", "Redirects",
                        RedirectController.ADMINISTER_REDIRECTS),
                RouteDefinition.admin(ViewsUiController.PATH, "views", "Views", ViewsUiController.ADMINISTER_VIEWS),
                RouteDefinition.admin(ViewsUiController.PATH + "/**", "views_actions", "Views",
                        ViewsUiController.ADMINISTER_VIEWS),
                RouteDefinition.frontEnd("/views/page/**", "views_page", "View"),
                RouteDefinition.frontEnd(SearchPageManager.PATH, "search", "Search"),
                RouteDefinition.frontEnd(BigPipe.NO_JS_PATH, "big_pipe_no_js", "Without JavaScript"),
                RouteDefinition.admin(SearchSettingsController.PATH, "search_settings", "Search settings",
                        SearchSettingsController.ADMINISTER_SEARCH),
                RouteDefinition.frontEnd(SearchPageManager.PATH + "/*", "search_page", "Search"),
                new RouteDefinition(ContactController.PATH + "/**", "contact", "Contact",
                        false, ContactPermissions.SITE_WIDE, false),
                new RouteDefinition("/user/*/contact", "personal_contact", "Contact",
                        false, ContactPermissions.PERSONAL, false),
                RouteDefinition.admin(
                        ContactFormAdminController.PATH + "/**",
                        "contact_form_actions",
                        "Contact forms",
                        ContactPermissions.ADMINISTER),
                RouteDefinition.admin(
                        ContactFormAdminController.PATH,
                        "contact_forms",
                        "Contact forms",
                        ContactPermissions.ADMINISTER),
                RouteDefinition.admin(
                        CommentAdminController.PATH + "/**",
                        "comment_admin_actions",
                        "Comments",
                        CommentPermissions.ADMINISTER_COMMENTS),
                RouteDefinition.admin(
                        CommentAdminController.PATH,
                        "comment_admin",
                        "Comments",
                        CommentPermissions.ADMINISTER_COMMENTS),
                RouteDefinition.admin(
                        TaxonomyController.PATH + "/**",
                        "taxonomy_actions",
                        "Taxonomy",
                        TaxonomyPermissions.ADMINISTER_TAXONOMY),
                RouteDefinition.admin(
                        TaxonomyController.PATH,
                        "taxonomy",
                        "Taxonomy",
                        TaxonomyPermissions.ADMINISTER_TAXONOMY),
                RouteDefinition.admin(
                        ModerationController.DASHBOARD_PATH,
                        "moderated_content",
                        "Moderated content",
                        NodePermissions.VIEW_ANY_UNPUBLISHED),
                RouteDefinition.admin(
                        WorkflowController.PATH + "/**",
                        "workflow_actions",
                        "Workflows",
                        Permissions.ADMINISTER_WORKFLOWS),
                RouteDefinition.admin(
                        WorkflowController.PATH,
                        "workflows",
                        "Workflows",
                        Permissions.ADMINISTER_WORKFLOWS),
                RouteDefinition.admin(
                        NodeTypeController.PATH + "/**",
                        "node_type_actions",
                        "Content types",
                        NodePermissions.ADMINISTER_CONTENT_TYPES),
                RouteDefinition.admin(
                        NodeTypeController.PATH,
                        "node_types",
                        "Content types",
                        NodePermissions.ADMINISTER_CONTENT_TYPES),
                RouteDefinition.admin(
                        TokenBrowserController.PATH,
                        "token_browser",
                        "Available tokens",
                        Permissions.ADMINISTER_SITE_CONFIGURATION),
                RouteDefinition.admin(
                        FieldUiController.PATH_PREFIX + "/**",
                        "field_ui",
                        "Manage fields",
                        Permissions.ADMINISTER_FIELDS),
                RouteDefinition.admin(
                        RolePermissionsController.PATH,
                        "role_permissions",
                        "Permissions",
                        Permissions.ADMINISTER_PERMISSIONS),
                RouteDefinition.admin(
                        PeopleController.PATH + "/**",
                        "people_actions",
                        "People",
                        Permissions.ADMINISTER_USERS),
                RouteDefinition.admin(
                        IpBanController.PATH + "/**",
                        "ip_ban_actions",
                        "Banned addresses",
                        Permissions.BAN_IP_ADDRESSES),
                RouteDefinition.admin(
                        IpBanController.PATH,
                        "ip_ban",
                        "Banned addresses",
                        Permissions.BAN_IP_ADDRESSES),
                RouteDefinition.admin(
                        PeopleController.PATH,
                        "people",
                        "People",
                        Permissions.ADMINISTER_USERS),
                RouteDefinition.admin(
                        MenuController.PATH + "/**",
                        "menu_actions",
                        "Menus",
                        Permissions.ADMINISTER_MENU),
                RouteDefinition.admin(
                        MenuController.PATH,
                        "menu",
                        "Menus",
                        Permissions.ADMINISTER_MENU),
                RouteDefinition.admin(
                        BlockLayoutController.PATH + "/**",
                        "block_actions",
                        "Block layout",
                        Permissions.ADMINISTER_BLOCKS),
                RouteDefinition.admin(
                        BlockLayoutController.PATH,
                        "block_layout",
                        "Block layout",
                        Permissions.ADMINISTER_BLOCKS),
                RouteDefinition.admin(
                        BlockContentTypeController.PATH + "/**",
                        "block_content_type_actions",
                        "Block types",
                        Permissions.ADMINISTER_BLOCKS),
                RouteDefinition.admin(
                        BlockContentTypeController.PATH,
                        "block_content_types",
                        "Block types",
                        Permissions.ADMINISTER_BLOCKS),
                RouteDefinition.admin(
                        CustomBlockController.PATH + "/**",
                        "custom_block_actions",
                        "Custom block library",
                        Permissions.ADMINISTER_BLOCKS),
                RouteDefinition.admin(
                        CustomBlockController.PATH,
                        "custom_blocks",
                        "Custom block library",
                        Permissions.ADMINISTER_BLOCKS));
    }
}
