package dev.springdrop.kernel.action;

import java.util.Map;

/**
 * An action the site offers: an action plugin with its settings, under a label
 * of its own. Stored as the config object {@code action.<id>}. A simple action
 * is offered without one, under its plugin's id and label.
 */
public record ActionConfig(String id, String label, String plugin, Map<String, Object> configuration) {

    public static final String CONFIG_PREFIX = "action";

    public ActionConfig {
        configuration = Map.copyOf(configuration);
    }

    public static String configName(String id) {
        return CONFIG_PREFIX + "." + id;
    }
}
