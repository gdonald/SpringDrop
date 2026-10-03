package dev.springdrop.kernel.image.effects;

import dev.springdrop.kernel.file.ImageSize;
import dev.springdrop.kernel.image.ImageEffect;
import dev.springdrop.kernel.image.ImageToolkit;
import dev.springdrop.kernel.image.ToolkitImage;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.Map;
import java.util.Optional;

/** Turns the image to shades of gray. */
@SpringDropPlugin(id = DesaturateEffect.ID, type = ImageEffect.class)
public class DesaturateEffect implements ImageEffect {

    public static final String ID = "image_desaturate";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Desaturate";
    }

    @Override
    public ToolkitImage apply(ImageToolkit toolkit, ToolkitImage image, Map<String, Object> settings) {
        return toolkit.desaturate(image);
    }

    @Override
    public Optional<ImageSize> transformedSize(ImageSize size, Map<String, Object> settings) {
        return Optional.of(size);
    }
}
