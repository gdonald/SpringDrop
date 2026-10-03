package dev.springdrop.kernel.filter;

import dev.springdrop.kernel.form.FormElement;
import java.util.List;
import java.util.Map;

/**
 * One step a text format runs text through on its way to the page, such as
 * keeping only some HTML tags or turning line breaks into paragraphs. A module
 * contributes one by annotating it with
 * {@code @SpringDropPlugin(type = TextFilter.class)}.
 */
public interface TextFilter {

    String label();

    /** The text after this step, given the settings the format gives it. */
    String process(String text, Map<String, Object> settings);

    /**
     * The HTML after the sanitizer has run, for a filter whose output the
     * sanitizer would remove, such as embedded media drawn by the site itself.
     * Most filters leave it as it is.
     */
    default String afterSanitizing(String html, Map<String, Object> settings) {
        return html;
    }

    /** The settings a format starts this filter with. */
    default Map<String, Object> defaultSettings() {
        return Map.of();
    }

    /** The elements this filter's settings are edited with, each named with the given prefix. */
    default List<FormElement> settingsForm(String prefix, Map<String, Object> settings) {
        return List.of();
    }

    /** The settings a submission of {@link #settingsForm} gives. */
    default Map<String, Object> settingsValues(String prefix, Map<String, String> submitted) {
        return Map.of();
    }
}
