package dev.springdrop.kernel.views;

import java.util.Map;

/** A pager, style, row, or access plugin of a view, with its settings. */
public record PluginConfig(String plugin, Map<String, Object> settings) {

    public PluginConfig {
        settings = Map.copyOf(settings);
    }

    public static PluginConfig of(String plugin) {
        return new PluginConfig(plugin, Map.of());
    }

    public Object setting(String name, Object fallback) {
        Object value = settings.get(name);
        return (value == null) ? fallback : value;
    }

    public int number(String name, int fallback) {
        String value = String.valueOf(setting(name, fallback));
        return value.matches("-?\\d{1,9}") ? Integer.parseInt(value) : fallback;
    }
}
