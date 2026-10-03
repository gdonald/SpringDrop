package dev.springdrop.kernel.views;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Changing a view's options a section at a time. A section of a display that
 * does not override it is the default display's, shared by every display that
 * does not override it either, so changing it changes theirs too.
 */
public final class ViewEditing {

    public static final String FIELDS = "fields";

    public static final String FILTERS = "filters";

    public static final String SORTS = "sorts";

    public static final String ARGUMENTS = "arguments";

    public static final String RELATIONSHIPS = "relationships";

    public static final List<String> SECTIONS = List.of(FIELDS, FILTERS, SORTS, ARGUMENTS, RELATIONSHIPS);

    public static final String PAGER = "pager";

    public static final String STYLE = "style";

    public static final String ROW = "row";

    public static final String ACCESS = "access";

    public static final List<String> KINDS = List.of(PAGER, STYLE, ROW, ACCESS);

    /** The plugin type each section's handlers and each kind of plugin are. */
    public static final Map<String, Class<? extends ViewPlugin>> PLUGIN_TYPES = Map.of(
            FIELDS, FieldHandler.class, FILTERS, FilterHandler.class, SORTS, SortHandler.class,
            ARGUMENTS, ArgumentHandler.class, RELATIONSHIPS, RelationshipHandler.class,
            PAGER, PagerPlugin.class, STYLE, StylePlugin.class, ROW, RowPlugin.class, ACCESS, AccessPlugin.class);

    private ViewEditing() {
    }

    /** A section of options, or null when they leave it to the default display. */
    public static List<HandlerConfig> section(ViewOptions options, String section) {
        return switch (section) {
            case FIELDS -> options.fields();
            case FILTERS -> options.filters();
            case SORTS -> options.sorts();
            case ARGUMENTS -> options.arguments();
            default -> options.relationships();
        };
    }

    public static ViewOptions withSection(ViewOptions options, String section, List<HandlerConfig> handlers) {
        return switch (section) {
            case FIELDS -> options.withFields(handlers);
            case FILTERS -> options.withFilters(handlers);
            case SORTS -> options.withSorts(handlers);
            case ARGUMENTS -> options.withArguments(handlers);
            default -> options.withRelationships(handlers);
        };
    }

    /** A kind of plugin of options, or null when they leave it to the default display. */
    public static PluginConfig plugin(ViewOptions options, String kind) {
        return switch (kind) {
            case PAGER -> options.pager();
            case STYLE -> options.style();
            case ROW -> options.row();
            default -> options.access();
        };
    }

    public static ViewOptions withPlugin(ViewOptions options, String kind, PluginConfig plugin) {
        return switch (kind) {
            case PAGER -> options.withPager(plugin);
            case STYLE -> options.withStyle(plugin);
            case ROW -> options.withRow(plugin);
            default -> options.withAccess(plugin);
        };
    }

    /** Whether a display has its own copy of a section or kind of plugin. */
    public static boolean overrides(ViewConfig view, String displayId, String part) {
        ViewDisplay display = view.display(displayId).orElseThrow();
        if (display.id().equals(ViewDisplay.DEFAULT)) {
            return true;
        }
        return SECTIONS.contains(part) ? section(display.overrides(), part) != null
                : plugin(display.overrides(), part) != null;
    }

    /** The display whose options a change to a part of a display goes to: its own, or the default display's. */
    private static ViewDisplay owner(ViewConfig view, String displayId, String part) {
        return overrides(view, displayId, part) ? view.display(displayId).orElseThrow() : view.defaultDisplay();
    }

    /** The view with a section of a display changed. */
    public static ViewConfig editSection(ViewConfig view, String displayId, String section,
            UnaryOperator<List<HandlerConfig>> change) {
        ViewDisplay owner = owner(view, displayId, section);
        List<HandlerConfig> current = section(owner.overrides(), section);
        List<HandlerConfig> changed = change.apply(current == null ? List.of() : List.copyOf(current));
        return view.withDisplay(new ViewDisplay(owner.id(), owner.plugin(), owner.title(),
                withSection(owner.overrides(), section, changed), owner.settings()));
    }

    /** The view with a kind of plugin of a display set. */
    public static ViewConfig editPlugin(ViewConfig view, String displayId, String kind, PluginConfig plugin) {
        ViewDisplay owner = owner(view, displayId, kind);
        return view.withDisplay(new ViewDisplay(owner.id(), owner.plugin(), owner.title(),
                withPlugin(owner.overrides(), kind, plugin), owner.settings()));
    }

    /**
     * The view with a display's part made its own, starting from the default
     * display's, or given back to the default display.
     */
    public static ViewConfig toggleOverride(ViewConfig view, String displayId, String part) {
        ViewDisplay display = view.display(displayId).orElseThrow();
        ViewOptions defaults = view.defaultDisplay().overrides();
        boolean own = overrides(view, displayId, part);
        ViewOptions changed;
        if (SECTIONS.contains(part)) {
            List<HandlerConfig> copied = section(defaults, part);
            changed = withSection(display.overrides(), part,
                    own ? null : new ArrayList<>(copied == null ? List.of() : copied));
        } else {
            changed = withPlugin(display.overrides(), part, own ? null : plugin(defaults, part));
        }
        return view.withDisplay(new ViewDisplay(display.id(), display.plugin(), display.title(), changed,
                display.settings()));
    }
}
