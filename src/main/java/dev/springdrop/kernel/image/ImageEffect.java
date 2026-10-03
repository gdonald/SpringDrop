package dev.springdrop.kernel.image;

import dev.springdrop.kernel.file.ImageSize;
import dev.springdrop.kernel.form.FormElement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One step an image style applies, such as scaling or cropping. An effect is a
 * plugin registered with {@code @SpringDropPlugin(type = ImageEffect.class)},
 * so a module adds one without core knowing about it. It is built from the
 * operations of the site's {@link ImageToolkit}, so it works with any toolkit.
 */
public interface ImageEffect {

    /** The prefix every settings element is named with. */
    String SETTINGS_PREFIX = "effect_";

    String id();

    String label();

    ToolkitImage apply(ImageToolkit toolkit, ToolkitImage image, Map<String, Object> settings);

    /**
     * The size an image of the given size comes out at, worked out without
     * touching the image, or nothing when the effect cannot tell in advance.
     */
    Optional<ImageSize> transformedSize(ImageSize size, Map<String, Object> settings);

    /** A short description of the settings for the style's list of effects. */
    default String summary(Map<String, Object> settings) {
        return "";
    }

    /** The elements the settings are edited with, filled from those settings. */
    default List<FormElement> settingsForm(Map<String, Object> settings) {
        return List.of();
    }

    /** The settings a submission of {@link #settingsForm} gives, once its rules have passed. */
    default Map<String, Object> settingsValues(Map<String, String> submitted) {
        return Map.of();
    }

    /** Errors the elements' own rules cannot catch, keyed by the element they belong to. */
    default Map<String, String> validateSettings(Map<String, String> submitted) {
        return Map.of();
    }
}
