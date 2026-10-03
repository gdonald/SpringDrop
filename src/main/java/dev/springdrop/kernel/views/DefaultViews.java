package dev.springdrop.kernel.views;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.file.FileEntityType;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.user.UserEntityType;
import dev.springdrop.kernel.views.handlers.BaseValueField;
import dev.springdrop.kernel.views.handlers.EntityLabelField;
import dev.springdrop.kernel.views.handlers.FullPager;
import dev.springdrop.kernel.views.handlers.LinksField;
import dev.springdrop.kernel.views.handlers.NonePager;
import dev.springdrop.kernel.views.handlers.OperationsField;
import dev.springdrop.kernel.views.handlers.PermissionAccess;
import dev.springdrop.kernel.views.handlers.ReferenceRelationship;
import dev.springdrop.kernel.views.handlers.RoleFilter;
import dev.springdrop.kernel.views.handlers.StandardSort;
import dev.springdrop.kernel.views.handlers.TermArgument;
import dev.springdrop.kernel.views.handlers.ValueFilter;
import dev.springdrop.kernel.views.rows.EntityRow;
import dev.springdrop.kernel.views.rows.FieldsRow;
import dev.springdrop.kernel.views.styles.TableStyle;
import dev.springdrop.kernel.views.styles.UnformattedStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The views a site starts with, added as the application starts when missing:
 * the content and files admin overviews, the front page listing and its feed,
 * the content tagged with a term, and the people overview. Each is an ordinary
 * view, edited like any other.
 */
@Component
public class DefaultViews {

    public static final String CONTENT = "content";

    public static final String FILES = "files";

    public static final String FRONTPAGE = "frontpage";

    public static final String TAXONOMY_TERM = "taxonomy_term";

    public static final String PEOPLE = "people";

    public static final String ACCESS_CONTENT_OVERVIEW = "access content overview";

    public static final String ACCESS_FILES_OVERVIEW = "access files overview";

    private final ViewManager views;

    public DefaultViews(ViewManager views) {
        this.views = views;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void install() {
        List.of(content(), files(), frontpage(), taxonomyTerm(), people()).forEach(view -> {
            if (views.find(view.id()).isEmpty()) {
                views.save(view);
            }
        });
    }

    private static HandlerConfig field(String id, String plugin, String property, Map<String, Object> settings) {
        return HandlerConfig.of(id, plugin, property, settings);
    }

    private static HandlerConfig exposed(String id, String property, Map<String, Object> settings) {
        Map<String, Object> merged = new LinkedHashMap<>(settings);
        merged.put(FilterHandler.EXPOSED, true);
        merged.put(FilterHandler.IDENTIFIER, id);
        return HandlerConfig.of(id, ValueFilter.ID, property, merged);
    }

    private static PluginConfig permission(String permission) {
        return new PluginConfig(PermissionAccess.ID, Map.of(PermissionAccess.PERMISSION, permission));
    }

    private static ViewDisplay defaults(String title, ViewOptions options, Map<String, Object> settings) {
        return new ViewDisplay(ViewDisplay.DEFAULT, ViewDisplay.DEFAULT, title, options, settings);
    }

    /** All content, for editors: a sortable table with exposed filters, the latest change first. */
    static ViewConfig content() {
        ViewOptions options = new ViewOptions(
                List.of(field("title", EntityLabelField.ID, "label", Map.of(FieldHandler.LABEL, "Title")),
                        field("type", BaseValueField.ID, "bundle", Map.of(FieldHandler.LABEL, "Content type")),
                        field("author", EntityLabelField.ID, "label", Map.of(FieldHandler.LABEL, "Author",
                                EntityLabelField.LINK, false)).through("author"),
                        field("status", BaseValueField.ID, BaseFieldDefinition.STATUS, Map.of(FieldHandler.LABEL,
                                "Status", BaseValueField.TRUE_LABEL, "Published", BaseValueField.FALSE_LABEL,
                                "Unpublished")),
                        field("changed", BaseValueField.ID, BaseFieldDefinition.CHANGED, Map.of(FieldHandler.LABEL,
                                "Updated")),
                        field("operations", OperationsField.ID, "id", Map.of())),
                List.of(exposed("title", "label", Map.of(ValueFilter.OPERATOR, "contains", "label", "Title")),
                        exposed("type", NodeEntityType.BUNDLE_KEY, Map.of("label", "Content type")),
                        exposed("status", BaseFieldDefinition.STATUS, Map.of("type", "boolean", "label", "Status",
                                "true_label", "Published", "false_label", "Unpublished"))),
                List.of(HandlerConfig.of("changed", StandardSort.ID, BaseFieldDefinition.CHANGED,
                        Map.of(SortHandler.ORDER, "desc"))),
                List.of(),
                List.of(HandlerConfig.of("author", ReferenceRelationship.ID, BaseFieldDefinition.OWNER, Map.of())),
                new PluginConfig(FullPager.ID, Map.of(PagerPlugin.ITEMS_PER_PAGE, 50)),
                new PluginConfig(TableStyle.ID, Map.of(TableStyle.SORTABLE, true)),
                PluginConfig.of(FieldsRow.ID),
                permission(ACCESS_CONTENT_OVERVIEW));
        return new ViewConfig(CONTENT, "Content", "Find and manage content.", NodeEntityType.ID, List.of(
                defaults("Content", options, Map.of(ViewRenderer.EMPTY_TEXT, "No content available.")),
                new ViewDisplay("page_1", ViewDisplay.PAGE, "Content", ViewOptions.INHERIT,
                        Map.of(ViewDisplay.PATH, "/admin/content"))));
    }

    /** The site's managed files, newest first. */
    static ViewConfig files() {
        ViewOptions options = new ViewOptions(
                List.of(field("filename", BaseValueField.ID, "label", Map.of(FieldHandler.LABEL, "Name")),
                        field("mime", BaseValueField.ID, FileEntityType.MIME, Map.of(FieldHandler.LABEL, "Kind")),
                        field("size", BaseValueField.ID, FileEntityType.SIZE, Map.of(FieldHandler.LABEL,
                                "Size in bytes")),
                        field("status", BaseValueField.ID, BaseFieldDefinition.STATUS, Map.of(FieldHandler.LABEL,
                                "Status", BaseValueField.TRUE_LABEL, "Permanent", BaseValueField.FALSE_LABEL,
                                "Temporary")),
                        field("created", BaseValueField.ID, BaseFieldDefinition.CREATED, Map.of(FieldHandler.LABEL,
                                "Uploaded"))),
                List.of(exposed("filename", "label", Map.of(ValueFilter.OPERATOR, "contains", "label", "Name")),
                        exposed("status", BaseFieldDefinition.STATUS, Map.of("type", "boolean", "label", "Status",
                                "true_label", "Permanent", "false_label", "Temporary"))),
                List.of(HandlerConfig.of("created", StandardSort.ID, BaseFieldDefinition.CREATED,
                        Map.of(SortHandler.ORDER, "desc"))),
                List.of(), List.of(),
                new PluginConfig(FullPager.ID, Map.of(PagerPlugin.ITEMS_PER_PAGE, 50)),
                new PluginConfig(TableStyle.ID, Map.of(TableStyle.SORTABLE, true)),
                PluginConfig.of(FieldsRow.ID),
                permission(ACCESS_FILES_OVERVIEW));
        return new ViewConfig(FILES, "Files", "The site's managed files.", FileEntityType.ID, List.of(
                defaults("Files", options, Map.of(ViewDisplay.ENTITY_ACCESS, false, ViewRenderer.EMPTY_TEXT,
                        "No files available.")),
                new ViewDisplay("page_1", ViewDisplay.PAGE, "Files", ViewOptions.INHERIT,
                        Map.of(ViewDisplay.PATH, "/admin/content/files"))));
    }

    private static List<HandlerConfig> newestFirst() {
        return List.of(HandlerConfig.of("sticky", StandardSort.ID, NodeEntityType.STICKY, Map.of(SortHandler.ORDER,
                        "desc")),
                HandlerConfig.of("created", StandardSort.ID, BaseFieldDefinition.CREATED, Map.of(SortHandler.ORDER,
                        "desc")),
                HandlerConfig.of("id", StandardSort.ID, "id", Map.of(SortHandler.ORDER, "desc")));
    }

    private static HandlerConfig published() {
        return HandlerConfig.of("status", ValueFilter.ID, BaseFieldDefinition.STATUS,
                Map.of(FilterHandler.VALUE, "true", "type", "boolean"));
    }

    /** Published content promoted to the front page as teasers, sticky first, then the newest. */
    static ViewConfig frontpage() {
        ViewOptions options = new ViewOptions(
                List.of(field("title", EntityLabelField.ID, "label", Map.of())),
                List.of(published(), HandlerConfig.of("promote", ValueFilter.ID, NodeEntityType.PROMOTE,
                        Map.of(FilterHandler.VALUE, "true", "type", "boolean"))),
                newestFirst(), List.of(), List.of(),
                new PluginConfig(FullPager.ID, Map.of(PagerPlugin.ITEMS_PER_PAGE, 10)),
                PluginConfig.of(UnformattedStyle.ID),
                new PluginConfig(EntityRow.ID, Map.of(EntityRow.VIEW_MODE, "teaser")),
                permission(NodePermissions.ACCESS_CONTENT));
        return new ViewConfig(FRONTPAGE, "Front page", "Content promoted to the front page.", NodeEntityType.ID,
                List.of(defaults("Front page", options, Map.of()),
                        new ViewDisplay("feed_1", ViewDisplay.FEED, "Front page feed", ViewOptions.INHERIT,
                                Map.of(ViewDisplay.PATH, "/rss.xml"))));
    }

    /** Published content tagged with the term given, as teasers, sticky first, then the newest. */
    static ViewConfig taxonomyTerm() {
        ViewOptions options = new ViewOptions(
                List.of(field("title", EntityLabelField.ID, "label", Map.of())),
                List.of(published()), newestFirst(),
                List.of(HandlerConfig.of("term", TermArgument.ID, NodeEntityType.ID,
                        Map.of(ArgumentHandler.DEFAULT_ACTION, ArgumentHandler.EMPTY))),
                List.of(),
                new PluginConfig(FullPager.ID, Map.of(PagerPlugin.ITEMS_PER_PAGE, 10)),
                PluginConfig.of(UnformattedStyle.ID),
                new PluginConfig(EntityRow.ID, Map.of(EntityRow.VIEW_MODE, "teaser")),
                permission(NodePermissions.ACCESS_CONTENT));
        return new ViewConfig(TAXONOMY_TERM, "Taxonomy term", "Content tagged with a term.", NodeEntityType.ID,
                List.of(defaults("Taxonomy term", options, Map.of())));
    }

    /** The accounts on the site, by name, with their mail, status, and the buttons managing them. */
    static ViewConfig people() {
        ViewOptions options = new ViewOptions(
                List.of(field("name", BaseValueField.ID, "label", Map.of(FieldHandler.LABEL, "Name")),
                        field("mail", BaseValueField.ID, UserEntityType.MAIL, Map.of(FieldHandler.LABEL, "Email")),
                        field("status", BaseValueField.ID, BaseFieldDefinition.STATUS, Map.of(FieldHandler.LABEL,
                                "Status", BaseValueField.TRUE_LABEL, "Active", BaseValueField.FALSE_LABEL,
                                "Blocked")),
                        field("operations", LinksField.ID, "id", Map.of(FieldHandler.LABEL, "Actions",
                                LinksField.LINKS, List.of(
                                        Map.of("label", "Edit", "path", "/admin/people/{id}/edit"),
                                        Map.of("label", "Close", "path", "/admin/people/{id}/cancel",
                                                "style", "danger"))))),
                List.of(exposed("name", "label", Map.of(ValueFilter.OPERATOR, "contains", "label", "Name")),
                        exposed("status", BaseFieldDefinition.STATUS, Map.of("type", "boolean", "label", "Status",
                                "true_label", "Active", "false_label", "Blocked")),
                        HandlerConfig.of("role", RoleFilter.ID, UserEntityType.ROLES, Map.of(FilterHandler.EXPOSED,
                                true, FilterHandler.IDENTIFIER, "role"))),
                List.of(HandlerConfig.of("name", StandardSort.ID, "label", Map.of())),
                List.of(), List.of(),
                PluginConfig.of(NonePager.ID),
                new PluginConfig(TableStyle.ID, Map.of(TableStyle.SORTABLE, false)),
                PluginConfig.of(FieldsRow.ID),
                permission(Permissions.ADMINISTER_USERS));
        return new ViewConfig(PEOPLE, "People", "The accounts on this site.", UserEntityType.ID,
                List.of(defaults("People", options, Map.of(ViewDisplay.ENTITY_ACCESS, false))));
    }
}
