package dev.springdrop.kernel.views;

/**
 * Shows one value of each result, such as its title or a field of it. A field
 * handler is a plugin registered with {@code @SpringDropPlugin(type = FieldHandler.class)}.
 */
public interface FieldHandler extends ViewPlugin {

    /** The setting naming the column's heading. */
    String LABEL = "label";

    /** The markup of the value for one result, escaped where it needs to be. */
    String render(ResultRow row, HandlerConfig config);

    /** Whether a table can be sorted by the field's property. */
    default boolean sortable() {
        return true;
    }

    /** The heading the value is shown under: the label setting, or the property. */
    default String heading(HandlerConfig config) {
        String label = config.text(LABEL);
        return label.isEmpty() ? config.property() : label;
    }
}
