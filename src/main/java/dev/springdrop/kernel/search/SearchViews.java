package dev.springdrop.kernel.search;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.user.UserEntityType;
import dev.springdrop.kernel.views.FilterHandler;
import dev.springdrop.kernel.views.HandlerConfig;
import dev.springdrop.kernel.views.PagerPlugin;
import dev.springdrop.kernel.views.PluginConfig;
import dev.springdrop.kernel.views.ViewConfig;
import dev.springdrop.kernel.views.ViewDisplay;
import dev.springdrop.kernel.views.ViewManager;
import dev.springdrop.kernel.views.ViewOptions;
import dev.springdrop.kernel.views.ViewRenderer;
import dev.springdrop.kernel.views.handlers.EntityLabelField;
import dev.springdrop.kernel.views.handlers.FullPager;
import dev.springdrop.kernel.views.handlers.PermissionAccess;
import dev.springdrop.kernel.views.handlers.ValueFilter;
import dev.springdrop.kernel.views.rows.FieldsRow;
import dev.springdrop.kernel.views.styles.UnformattedStyle;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** The views the default search pages draw, added when missing. */
@Component
public class SearchViews {

    public static final String CONTENT = "search_content";

    public static final String USERS = "search_users";

    public static final String SEARCH_CONTENT = "search content";

    public static final String SEARCH_USERS = "search users";

    private final ViewManager views;

    public SearchViews(ViewManager views) {
        this.views = views;
    }

    public void install() {
        for (ViewConfig view : List.of(content(), users())) {
            if (views.find(view.id()).isEmpty()) {
                views.save(view);
            }
        }
    }

    private static HandlerConfig keywords(String entityType) {
        return HandlerConfig.of("keys", SearchKeywordsFilter.ID, "id", Map.of(FilterHandler.EXPOSED, true,
                FilterHandler.IDENTIFIER, SearchExcerptField.DEFAULT_IDENTIFIER, SearchKeywordsFilter.ENTITY_TYPE,
                entityType, "label", "Keywords"));
    }

    private static ViewOptions options(List<HandlerConfig> fields, List<HandlerConfig> filters, String permission) {
        return new ViewOptions(fields, filters, List.of(), List.of(), List.of(),
                new PluginConfig(FullPager.ID, Map.of(PagerPlugin.ITEMS_PER_PAGE, 10)),
                PluginConfig.of(UnformattedStyle.ID), PluginConfig.of(FieldsRow.ID),
                new PluginConfig(PermissionAccess.ID, Map.of(PermissionAccess.PERMISSION, permission)));
    }

    /** Content holding the keywords, best match first, each title linked over an excerpt. */
    static ViewConfig content() {
        ViewOptions options = options(List.of(
                HandlerConfig.of("title", EntityLabelField.ID, "label", Map.of()),
                HandlerConfig.of("excerpt", SearchExcerptField.ID, "id", Map.of())),
                List.of(keywords(NodeEntityType.ID)), SEARCH_CONTENT);
        return new ViewConfig(CONTENT, "Content search", "Content holding the keywords.", NodeEntityType.ID,
                List.of(new ViewDisplay(ViewDisplay.DEFAULT, ViewDisplay.DEFAULT, "Content search", options,
                        Map.of(ViewRenderer.EMPTY_TEXT, "Your search yielded no results."))));
    }

    /** Active accounts whose names hold the keywords, best match first. */
    static ViewConfig users() {
        ViewOptions options = options(List.of(
                HandlerConfig.of("name", EntityLabelField.ID, "label", Map.of(EntityLabelField.LINK, false))),
                List.of(keywords(UserEntityType.ID), HandlerConfig.of("status", ValueFilter.ID,
                        BaseFieldDefinition.STATUS, Map.of(FilterHandler.VALUE, "true", "type", "boolean"))),
                SEARCH_USERS);
        return new ViewConfig(USERS, "User search", "Active accounts whose names hold the keywords.",
                UserEntityType.ID, List.of(new ViewDisplay(ViewDisplay.DEFAULT, ViewDisplay.DEFAULT, "User search",
                        options, Map.of(ViewDisplay.ENTITY_ACCESS, false, ViewRenderer.EMPTY_TEXT,
                                "Your search yielded no results."))));
    }
}
