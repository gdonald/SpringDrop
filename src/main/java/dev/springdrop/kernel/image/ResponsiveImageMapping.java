package dev.springdrop.kernel.image;

import java.util.List;

/**
 * What a responsive image style draws at one breakpoint and pixel density:
 * either a single image style, or several image styles offered by width with a
 * {@code sizes} attribute telling the browser how wide the image shows.
 */
public record ResponsiveImageMapping(
        String breakpoint, String multiplier, String type, String imageStyle, String sizes,
        List<String> sizesImageStyles) {

    public static final String IMAGE_STYLE = "image_style";

    public static final String SIZES = "sizes";

    public ResponsiveImageMapping {
        sizesImageStyles = List.copyOf(sizesImageStyles);
    }

    public static ResponsiveImageMapping single(String breakpoint, String multiplier, String imageStyle) {
        return new ResponsiveImageMapping(breakpoint, multiplier, IMAGE_STYLE, imageStyle, "", List.of());
    }

    public static ResponsiveImageMapping bySize(String breakpoint, String sizes, List<String> imageStyles) {
        return new ResponsiveImageMapping(breakpoint, "1x", SIZES, "", sizes, imageStyles);
    }
}
