package dev.springdrop.kernel.block;

import java.util.List;
import java.util.Map;

/**
 * One block placed in one region of one theme: which plugin draws it, what it is
 * labeled and whether the label shows, where it sits among the region's other
 * blocks, the plugin's settings, and the conditions it shows under. Stored as a
 * config entity named {@code block.block.<id>}, so placements ship with the
 * rest of a site's configuration.
 */
public record BlockPlacement(
        String id,
        String theme,
        String region,
        String plugin,
        String label,
        boolean labelDisplay,
        int weight,
        Map<String, Object> settings,
        List<ConditionConfig> visibility) {

    public static final String CONFIG_PREFIX = "block.block";

    /** The tag every rendering of any placement carries, invalidated when placements change. */
    public static final String LIST_CACHE_TAG = "config:block_list";

    public BlockPlacement {
        settings = Map.copyOf(settings);
        visibility = List.copyOf(visibility);
    }

    public static BlockPlacement of(String id, String theme, String region, String plugin, String label) {
        return new BlockPlacement(id, theme, region, plugin, label, true, 0, Map.of(), List.of());
    }

    public static String configName(String id) {
        return CONFIG_PREFIX + "." + id;
    }

    /** The tag invalidating what this placement rendered. */
    public String cacheTag() {
        return "config:" + configName(id);
    }

    /** What this placement draws, apart from where it is placed and when it shows. */
    public BlockInstance instance() {
        return new BlockInstance(id, plugin, label, labelDisplay, settings);
    }

    public BlockPlacement inRegion(String newRegion) {
        return new BlockPlacement(id, theme, newRegion, plugin, label, labelDisplay, weight, settings, visibility);
    }

    public BlockPlacement withWeight(int newWeight) {
        return new BlockPlacement(id, theme, region, plugin, label, labelDisplay, newWeight, settings, visibility);
    }

    public BlockPlacement withSettings(Map<String, Object> newSettings) {
        return new BlockPlacement(id, theme, region, plugin, label, labelDisplay, weight, newSettings, visibility);
    }

    public BlockPlacement withVisibility(List<ConditionConfig> conditions) {
        return new BlockPlacement(id, theme, region, plugin, label, labelDisplay, weight, settings, conditions);
    }

    public BlockPlacement withoutLabel() {
        return new BlockPlacement(id, theme, region, plugin, label, false, weight, settings, visibility);
    }
}
