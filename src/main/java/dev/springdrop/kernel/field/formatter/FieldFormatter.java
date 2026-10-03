package dev.springdrop.kernel.field.formatter;

import dev.springdrop.kernel.form.FormElement;
import java.util.List;
import java.util.Map;

/**
 * Renders one value of a field for reading. A formatter is a plugin registered
 * with {@code @SpringDropPlugin(type = FieldFormatter.class)}, so a module adds
 * one without core knowing about it. Multi-value handling is done around it: it
 * is asked for one value at a time.
 */
public interface FieldFormatter {

    String id();

    /** The markup for one value, already escaped where it needs to be. */
    String render(FormatterContext context, Object value);

    /**
     * The elements Manage display edits the settings with, each named starting
     * with the prefix, filled from the settings. A formatter without settings
     * offers none, and the settings it has are kept as they are.
     */
    default List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of();
    }

    /** The settings a submission of {@link #settingsForm} gives. */
    default Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        return Map.of();
    }
}
