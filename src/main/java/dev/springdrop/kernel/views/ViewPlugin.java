package dev.springdrop.kernel.views;

import dev.springdrop.kernel.form.FormElement;
import java.util.List;
import java.util.Map;

/** What every Views plugin has: a label, and settings edited on the Views UI. */
public interface ViewPlugin {

    String id();

    String label();

    /** The elements the settings are edited with, each named starting with the prefix. */
    default List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of();
    }

    /** The settings a submission of {@link #settingsForm} gives. */
    default Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        return Map.of();
    }
}
