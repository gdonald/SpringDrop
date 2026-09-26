package dev.springdrop.kernel.block;

import java.util.Map;

/**
 * One visibility condition as a placement stores it: the condition plugin, its
 * settings, and whether the block shows when the condition fails rather than
 * when it holds.
 */
public record ConditionConfig(String plugin, Map<String, Object> settings, boolean negate) {

    public ConditionConfig {
        settings = Map.copyOf(settings);
    }

    public static ConditionConfig of(String plugin, Map<String, Object> settings) {
        return new ConditionConfig(plugin, settings, false);
    }

    public ConditionConfig negated() {
        return new ConditionConfig(plugin, settings, true);
    }
}
