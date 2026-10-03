package dev.springdrop.kernel.block;

import java.util.Map;

/**
 * One configured block: which plugin draws it, what it is labeled and whether
 * the label shows, and the plugin's settings. A theme placement and a block in
 * a layout section each carry one, alongside where they put it.
 */
public record BlockInstance(String id, String plugin, String label, boolean labelDisplay, Map<String, Object> settings) {

    public BlockInstance {
        settings = Map.copyOf(settings);
    }

    public static BlockInstance of(String id, String plugin, String label) {
        return new BlockInstance(id, plugin, label, true, Map.of());
    }

    public BlockInstance withSettings(Map<String, Object> newSettings) {
        return new BlockInstance(id, plugin, label, labelDisplay, newSettings);
    }

    public BlockInstance withoutLabel() {
        return new BlockInstance(id, plugin, label, false, settings);
    }
}
