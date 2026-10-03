package dev.springdrop.kernel.views;

import java.util.Map;

/**
 * One field, filter, sort, contextual filter, or relationship of a view: its
 * id within the view, the plugin handling it, the relationship whose entity it
 * reads ({@link #BASE} for the view's own entity), the property it reads, a base
 * key or a field name, and the plugin's settings.
 */
public record HandlerConfig(String id, String plugin, String relationship, String property,
        Map<String, Object> settings) {

    /** Names the view's own entity in place of a relationship. */
    public static final String BASE = "none";

    public HandlerConfig {
        settings = Map.copyOf(settings);
    }

    public static HandlerConfig of(String id, String plugin, String property, Map<String, Object> settings) {
        return new HandlerConfig(id, plugin, BASE, property, settings);
    }

    public HandlerConfig through(String relationshipId) {
        return new HandlerConfig(id, plugin, relationshipId, property, settings);
    }

    public Object setting(String name, Object fallback) {
        Object value = settings.get(name);
        return (value == null) ? fallback : value;
    }

    public String text(String name) {
        return String.valueOf(setting(name, ""));
    }

    public boolean flag(String name) {
        return Boolean.TRUE.equals(settings.get(name)) || "true".equals(settings.get(name));
    }
}
