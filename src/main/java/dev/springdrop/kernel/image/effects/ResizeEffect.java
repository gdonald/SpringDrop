package dev.springdrop.kernel.image.effects;

import dev.springdrop.kernel.file.ImageSize;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.image.ImageEffect;
import dev.springdrop.kernel.image.ImageToolkit;
import dev.springdrop.kernel.image.ToolkitImage;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Stretches or squeezes the image to an exact width and height. */
@SpringDropPlugin(id = ResizeEffect.ID, type = ImageEffect.class)
public class ResizeEffect implements ImageEffect {

    public static final String ID = "image_resize";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Resize";
    }

    @Override
    public ToolkitImage apply(ImageToolkit toolkit, ToolkitImage image, Map<String, Object> settings) {
        return toolkit.resize(image, Pixels.width(settings), Pixels.height(settings));
    }

    @Override
    public Optional<ImageSize> transformedSize(ImageSize size, Map<String, Object> settings) {
        return Optional.of(new ImageSize(Pixels.width(settings), Pixels.height(settings)));
    }

    @Override
    public String summary(Map<String, Object> settings) {
        return Pixels.width(settings) + " by " + Pixels.height(settings);
    }

    @Override
    public List<FormElement> settingsForm(Map<String, Object> settings) {
        return exactSize(settings);
    }

    static List<FormElement> exactSize(Map<String, Object> settings) {
        return List.of(
                Pixels.input(Pixels.WIDTH, "Width", settings).markRequired(),
                Pixels.input(Pixels.HEIGHT, "Height", settings).markRequired());
    }

    @Override
    public Map<String, Object> settingsValues(Map<String, String> submitted) {
        return exactSizeValues(submitted);
    }

    static Map<String, Object> exactSizeValues(Map<String, String> submitted) {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put(Pixels.WIDTH, Pixels.submitted(submitted, Pixels.WIDTH).orElse(1));
        settings.put(Pixels.HEIGHT, Pixels.submitted(submitted, Pixels.HEIGHT).orElse(1));
        return settings;
    }
}
