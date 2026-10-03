package dev.springdrop.kernel.image;

import java.util.List;

/**
 * Draws an image differently at each breakpoint of a theme's breakpoint group,
 * as a {@code picture} element, with a fallback image style for browsers that
 * pick no source. Stored as the config object {@code responsive_image.styles.<id>}.
 */
public record ResponsiveImageStyle(
        String id, String label, String breakpointGroup, String fallbackImageStyle,
        List<ResponsiveImageMapping> mappings) {

    public static final String CONFIG_PREFIX = "responsive_image.styles";

    /** Names the original image in place of an image style. */
    public static final String ORIGINAL = "_original";

    public ResponsiveImageStyle {
        mappings = List.copyOf(mappings);
    }

    public static String configName(String id) {
        return CONFIG_PREFIX + "." + id;
    }

    public List<ResponsiveImageMapping> mappingsFor(String breakpoint) {
        return mappings.stream().filter(mapping -> mapping.breakpoint().equals(breakpoint)).toList();
    }
}
