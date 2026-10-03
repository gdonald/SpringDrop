package dev.springdrop.kernel.image.effects;

import dev.springdrop.kernel.file.ImageSize;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.image.ImageEffect;
import dev.springdrop.kernel.image.ImageToolkit;
import dev.springdrop.kernel.image.ToolkitImage;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Scales the image until it covers a width and height, keeping its
 * proportions, then crops what overhangs evenly from both sides, so the result
 * is exactly that size.
 */
@SpringDropPlugin(id = ScaleAndCropEffect.ID, type = ImageEffect.class)
public class ScaleAndCropEffect implements ImageEffect {

    public static final String ID = "image_scale_and_crop";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Scale and crop";
    }

    @Override
    public ToolkitImage apply(ImageToolkit toolkit, ToolkitImage image, Map<String, Object> settings) {
        int width = Pixels.width(settings);
        int height = Pixels.height(settings);
        double factor = Math.max(width / (double) image.width(), height / (double) image.height());
        int scaledWidth = Math.max(width, (int) Math.round(image.width() * factor));
        int scaledHeight = Math.max(height, (int) Math.round(image.height() * factor));
        ToolkitImage scaled = toolkit.resize(image, scaledWidth, scaledHeight);
        return toolkit.crop(scaled, (scaledWidth - width) / 2, (scaledHeight - height) / 2, width, height);
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
        return ResizeEffect.exactSize(settings);
    }

    @Override
    public Map<String, Object> settingsValues(Map<String, String> submitted) {
        return ResizeEffect.exactSizeValues(submitted);
    }
}
