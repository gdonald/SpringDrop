package dev.springdrop.kernel.theme;

import dev.springdrop.kernel.event.AlterEvent;
import java.util.List;
import java.util.Map;

/**
 * Published once a template has been chosen and before its variables reach the
 * renderer, so a theme or module can add, replace, or drop variables. Listeners
 * mutate the map in place, the way Drupal's preprocess functions do.
 */
public class ThemePreprocessEvent extends AlterEvent<Map<String, Object>> {

    private final String hook;
    private final String template;
    private final List<String> suggestions;

    public ThemePreprocessEvent(
            String hook, String template, List<String> suggestions, Map<String, Object> variables) {
        super(variables);
        this.hook = hook;
        this.template = template;
        this.suggestions = List.copyOf(suggestions);
    }

    /** The least specific suggestion, naming what is being themed. */
    public String hook() {
        return hook;
    }

    /** The template path resolution settled on. */
    public String template() {
        return template;
    }

    public List<String> suggestions() {
        return suggestions;
    }
}
