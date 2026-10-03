package dev.springdrop.kernel.image.effects;

import dev.springdrop.kernel.file.ImageSize;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.image.ImageEffect;
import dev.springdrop.kernel.image.ImageToolkit;
import dev.springdrop.kernel.image.ToolkitImage;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cuts a width and height out of the image, anchored to one of nine points,
 * without scaling it. A side the image is shorter than comes out transparent.
 */
@SpringDropPlugin(id = CropEffect.ID, type = ImageEffect.class)
public class CropEffect implements ImageEffect {

    public static final String ID = "image_crop";

    public static final String ANCHOR = "anchor";

    public static final String DEFAULT_ANCHOR = "center-center";

    private static final List<String> HORIZONTAL = List.of("left", "center", "right");

    private static final List<String> VERTICAL = List.of("top", "center", "bottom");

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Crop";
    }

    @Override
    public ToolkitImage apply(ImageToolkit toolkit, ToolkitImage image, Map<String, Object> settings) {
        int width = Pixels.width(settings);
        int height = Pixels.height(settings);
        String[] anchor = anchor(settings).split("-");
        return toolkit.crop(image, offset(anchor[0], image.width(), width), offset(anchor[1], image.height(), height),
                width, height);
    }

    private static int offset(String anchor, int source, int target) {
        return switch (anchor) {
            case "left", "top" -> 0;
            case "right", "bottom" -> source - target;
            default -> (source - target) / 2;
        };
    }

    static String anchor(Map<String, ?> settings) {
        String anchor = String.valueOf(settings.get(ANCHOR));
        return anchors().contains(anchor) ? anchor : DEFAULT_ANCHOR;
    }

    static List<String> anchors() {
        List<String> anchors = new ArrayList<>();
        VERTICAL.forEach(vertical -> HORIZONTAL.forEach(horizontal -> anchors.add(horizontal + "-" + vertical)));
        return anchors;
    }

    @Override
    public Optional<ImageSize> transformedSize(ImageSize size, Map<String, Object> settings) {
        return Optional.of(new ImageSize(Pixels.width(settings), Pixels.height(settings)));
    }

    @Override
    public String summary(Map<String, Object> settings) {
        return Pixels.width(settings) + " by " + Pixels.height(settings) + " from the " + anchor(settings);
    }

    @Override
    public List<FormElement> settingsForm(Map<String, Object> settings) {
        List<FormElement> elements = new ArrayList<>(ResizeEffect.exactSize(settings));
        elements.add(FormElement.of(ElementType.SELECT, Pixels.name(ANCHOR))
                .label("Anchor")
                .markRequired()
                .value(anchor(settings))
                .options(anchors().stream().map(anchor -> new SelectOption(anchor, anchor)).toList()));
        return elements;
    }

    @Override
    public Map<String, Object> settingsValues(Map<String, String> submitted) {
        Map<String, Object> settings = ResizeEffect.exactSizeValues(submitted);
        settings.put(ANCHOR, anchor(Map.of(ANCHOR, submitted.getOrDefault(Pixels.name(ANCHOR), ""))));
        return settings;
    }
}
