package dev.springdrop.kernel.image.effects;

import dev.springdrop.kernel.file.ImageSize;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.image.ImageEffect;
import dev.springdrop.kernel.image.ImageToolkit;
import dev.springdrop.kernel.image.ToolkitImage;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Scales the image to fit inside a width, a height, or both, keeping its
 * proportions. A smaller image is left as it is unless upscaling is allowed.
 */
@SpringDropPlugin(id = ScaleEffect.ID, type = ImageEffect.class)
public class ScaleEffect implements ImageEffect {

    public static final String ID = "image_scale";

    public static final String UPSCALE = "upscale";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Scale";
    }

    @Override
    public ToolkitImage apply(ImageToolkit toolkit, ToolkitImage image, Map<String, Object> settings) {
        ImageSize target = scaled(new ImageSize(image.width(), image.height()), settings);
        return (target.width() == image.width() && target.height() == image.height())
                ? image : toolkit.resize(image, target.width(), target.height());
    }

    @Override
    public Optional<ImageSize> transformedSize(ImageSize size, Map<String, Object> settings) {
        return Optional.of(scaled(size, settings));
    }

    static ImageSize scaled(ImageSize size, Map<String, Object> settings) {
        Optional<Integer> width = Pixels.of(settings, Pixels.WIDTH);
        Optional<Integer> height = Pixels.of(settings, Pixels.HEIGHT);
        double factor = Math.min(
                width.map(pixels -> pixels / (double) size.width()).orElse(Double.MAX_VALUE),
                height.map(pixels -> pixels / (double) size.height()).orElse(Double.MAX_VALUE));
        if (factor == Double.MAX_VALUE || (factor > 1 && !Boolean.TRUE.equals(settings.get(UPSCALE)))) {
            return size;
        }
        return new ImageSize(Math.max(1, (int) Math.round(size.width() * factor)),
                Math.max(1, (int) Math.round(size.height() * factor)));
    }

    @Override
    public String summary(Map<String, Object> settings) {
        String box = Pixels.of(settings, Pixels.WIDTH).map(String::valueOf).orElse("any width") + " by "
                + Pixels.of(settings, Pixels.HEIGHT).map(String::valueOf).orElse("any height");
        return Boolean.TRUE.equals(settings.get(UPSCALE)) ? box + ", upscaling allowed" : box;
    }

    @Override
    public List<FormElement> settingsForm(Map<String, Object> settings) {
        return List.of(
                Pixels.input(Pixels.WIDTH, "Width", settings).requiredWhen(Pixels.name(Pixels.HEIGHT), ""),
                Pixels.input(Pixels.HEIGHT, "Height", settings).requiredWhen(Pixels.name(Pixels.WIDTH), ""),
                FormElement.of(ElementType.CHECKBOX, Pixels.name(UPSCALE))
                        .label("Allow upscaling")
                        .value(Boolean.TRUE.equals(settings.get(UPSCALE))));
    }

    @Override
    public Map<String, Object> settingsValues(Map<String, String> submitted) {
        Map<String, Object> settings = new LinkedHashMap<>();
        Pixels.submitted(submitted, Pixels.WIDTH).ifPresent(width -> settings.put(Pixels.WIDTH, width));
        Pixels.submitted(submitted, Pixels.HEIGHT).ifPresent(height -> settings.put(Pixels.HEIGHT, height));
        settings.put(UPSCALE, FormRenderer.CHECKED_VALUE.equals(submitted.get(Pixels.name(UPSCALE))));
        return settings;
    }
}
