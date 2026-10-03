package dev.springdrop.kernel.layout;

import java.util.List;

/**
 * Whether one bundle's view mode is drawn by Layout Builder, the sections it is
 * drawn with when it is, and whether each entity of the bundle may replace them
 * with a layout of its own. Stored as the config entity
 * {@code layout_builder.display.<entity type>.<bundle>.<mode>}, beside the view
 * display whose field list it replaces.
 */
public record LayoutBuilderDisplay(
        String entityTypeId,
        String bundle,
        String mode,
        boolean enabled,
        boolean allowOverrides,
        List<Section> sections) {

    public static final String CONFIG_PREFIX = "layout_builder.display";

    public LayoutBuilderDisplay {
        sections = List.copyOf(sections);
    }

    public static String configName(String entityTypeId, String bundle, String mode) {
        return CONFIG_PREFIX + "." + entityTypeId + "." + bundle + "." + mode;
    }

    /** The tag invalidating what this layout drew. */
    public String cacheTag() {
        return "config:" + configName(entityTypeId, bundle, mode);
    }

    public static LayoutBuilderDisplay of(String entityTypeId, String bundle, String mode, List<Section> sections) {
        return new LayoutBuilderDisplay(entityTypeId, bundle, mode, true, false, sections);
    }

    public LayoutBuilderDisplay withEnabled(boolean on) {
        return new LayoutBuilderDisplay(entityTypeId, bundle, mode, on, allowOverrides, sections);
    }

    public LayoutBuilderDisplay withOverridesAllowed(boolean allowed) {
        return new LayoutBuilderDisplay(entityTypeId, bundle, mode, enabled, allowed, sections);
    }

    public LayoutBuilderDisplay withSections(List<Section> newSections) {
        return new LayoutBuilderDisplay(entityTypeId, bundle, mode, enabled, allowOverrides, newSections);
    }
}
