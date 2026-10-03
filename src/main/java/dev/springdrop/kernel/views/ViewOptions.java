package dev.springdrop.kernel.views;

import java.util.List;

/**
 * What a view lists and how: its fields, filters, sorts, contextual filters,
 * and relationships, and its pager, style, row, and access plugins. A display
 * leaves a part null to take the view's default display's.
 */
public record ViewOptions(
        List<HandlerConfig> fields,
        List<HandlerConfig> filters,
        List<HandlerConfig> sorts,
        List<HandlerConfig> arguments,
        List<HandlerConfig> relationships,
        PluginConfig pager,
        PluginConfig style,
        PluginConfig row,
        PluginConfig access) {

    /** Options that take every part from the default display. */
    public static final ViewOptions INHERIT = new ViewOptions(null, null, null, null, null, null, null, null, null);

    /** The options with each part this one leaves null taken from the defaults. */
    public ViewOptions over(ViewOptions defaults) {
        return new ViewOptions(
                fields != null ? fields : defaults.fields,
                filters != null ? filters : defaults.filters,
                sorts != null ? sorts : defaults.sorts,
                arguments != null ? arguments : defaults.arguments,
                relationships != null ? relationships : defaults.relationships,
                pager != null ? pager : defaults.pager,
                style != null ? style : defaults.style,
                row != null ? row : defaults.row,
                access != null ? access : defaults.access);
    }

    public ViewOptions withFields(List<HandlerConfig> newFields) {
        return new ViewOptions(newFields, filters, sorts, arguments, relationships, pager, style, row, access);
    }

    public ViewOptions withFilters(List<HandlerConfig> newFilters) {
        return new ViewOptions(fields, newFilters, sorts, arguments, relationships, pager, style, row, access);
    }

    public ViewOptions withSorts(List<HandlerConfig> newSorts) {
        return new ViewOptions(fields, filters, newSorts, arguments, relationships, pager, style, row, access);
    }

    public ViewOptions withArguments(List<HandlerConfig> newArguments) {
        return new ViewOptions(fields, filters, sorts, newArguments, relationships, pager, style, row, access);
    }

    public ViewOptions withRelationships(List<HandlerConfig> newRelationships) {
        return new ViewOptions(fields, filters, sorts, arguments, newRelationships, pager, style, row, access);
    }

    public ViewOptions withPager(PluginConfig newPager) {
        return new ViewOptions(fields, filters, sorts, arguments, relationships, newPager, style, row, access);
    }

    public ViewOptions withStyle(PluginConfig newStyle) {
        return new ViewOptions(fields, filters, sorts, arguments, relationships, pager, newStyle, row, access);
    }

    public ViewOptions withRow(PluginConfig newRow) {
        return new ViewOptions(fields, filters, sorts, arguments, relationships, pager, style, newRow, access);
    }

    public ViewOptions withAccess(PluginConfig newAccess) {
        return new ViewOptions(fields, filters, sorts, arguments, relationships, pager, style, row, newAccess);
    }
}
