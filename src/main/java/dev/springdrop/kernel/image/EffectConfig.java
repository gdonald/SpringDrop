package dev.springdrop.kernel.image;

import java.util.Map;

/** One effect of an image style: its own id within the style, the plugin, its weight, and its settings. */
public record EffectConfig(String id, String plugin, int weight, Map<String, Object> settings) {

    public EffectConfig {
        settings = Map.copyOf(settings);
    }
}
