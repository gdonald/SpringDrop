package dev.springdrop.kernel.image.effects;

import dev.springdrop.kernel.file.ImageSize;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.image.ImageEffect;
import dev.springdrop.kernel.image.ImageToolkit;
import dev.springdrop.kernel.image.ToolkitImage;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns the image clockwise by a number of degrees. The image grows to hold the
 * turned picture, and the corners a turn that is not a right angle uncovers are
 * filled with the background color, or left transparent without one.
 */
@SpringDropPlugin(id = RotateEffect.ID, type = ImageEffect.class)
public class RotateEffect implements ImageEffect {

    public static final String ID = "image_rotate";

    public static final String DEGREES = "degrees";

    public static final String BACKGROUND = "background";

    static final String DEGREES_PATTERN = "-?\\d{1,3}";

    static final String COLOR_PATTERN = "(#[0-9a-fA-F]{6})?";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Rotate";
    }

    @Override
    public ToolkitImage apply(ImageToolkit toolkit, ToolkitImage image, Map<String, Object> settings) {
        return toolkit.rotate(image, degrees(settings), background(settings));
    }

    @Override
    public Optional<ImageSize> transformedSize(ImageSize size, Map<String, Object> settings) {
        double radians = Math.toRadians(degrees(settings));
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));
        return Optional.of(new ImageSize((int) Math.round(size.width() * cos + size.height() * sin),
                (int) Math.round(size.width() * sin + size.height() * cos)));
    }

    static int degrees(Map<String, ?> settings) {
        String degrees = String.valueOf(settings.get(DEGREES));
        return degrees.matches(DEGREES_PATTERN) ? Integer.parseInt(degrees) : 0;
    }

    static String background(Map<String, ?> settings) {
        Object stored = settings.get(BACKGROUND);
        String color = (stored == null) ? "" : stored.toString();
        return color.matches(COLOR_PATTERN) ? color : "";
    }

    @Override
    public String summary(Map<String, Object> settings) {
        String turn = degrees(settings) + " degrees";
        return background(settings).isEmpty() ? turn : turn + " on " + background(settings);
    }

    @Override
    public List<FormElement> settingsForm(Map<String, Object> settings) {
        return List.of(
                FormElement.of(ElementType.NUMBER, Pixels.name(DEGREES))
                        .label("Degrees")
                        .description("Clockwise, from -360 to 360.")
                        .markRequired()
                        .value(String.valueOf(degrees(settings)))
                        .rule(ValidationRule.range("-360", "360")),
                FormElement.of(ElementType.TEXTFIELD, Pixels.name(BACKGROUND))
                        .label("Background color")
                        .description("Written #rrggbb, such as #ffffff. Blank for transparent.")
                        .value(background(settings))
                        .rule(ValidationRule.pattern(COLOR_PATTERN).withMessage("Write the color as #rrggbb.")));
    }

    @Override
    public Map<String, Object> settingsValues(Map<String, String> submitted) {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put(DEGREES, degrees(Map.of(DEGREES, submitted.getOrDefault(Pixels.name(DEGREES), "").strip())));
        settings.put(BACKGROUND, background(Map.of(BACKGROUND,
                submitted.getOrDefault(Pixels.name(BACKGROUND), "").strip())));
        return settings;
    }
}
