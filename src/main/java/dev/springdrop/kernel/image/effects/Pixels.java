package dev.springdrop.kernel.image.effects;

import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.image.ImageEffect;
import java.util.Map;
import java.util.Optional;

/** The width and height settings effects share, and reading them back. */
final class Pixels {

    static final String WIDTH = "width";

    static final String HEIGHT = "height";

    static final String PATTERN = "[1-9]\\d{0,4}";

    static final String MESSAGE = "Use a whole number of pixels from 1 to 99999.";

    private Pixels() {
    }

    static String name(String setting) {
        return ImageEffect.SETTINGS_PREFIX + setting;
    }

    static FormElement input(String setting, String label, Map<String, Object> settings) {
        return FormElement.of(ElementType.NUMBER, name(setting))
                .label(label)
                .value(of(settings, setting).map(String::valueOf).orElse(""))
                .attribute("min", "1")
                .rule(ValidationRule.pattern(PATTERN).withMessage(MESSAGE));
    }

    static Optional<Integer> of(Map<String, ?> settings, String setting) {
        Object value = settings.get(setting);
        return Optional.ofNullable(value).map(String::valueOf).filter(text -> text.matches(PATTERN))
                .map(Integer::valueOf);
    }

    static Optional<Integer> submitted(Map<String, String> submitted, String setting) {
        return of(Map.of(setting, submitted.getOrDefault(name(setting), "").strip()), setting);
    }

    static int width(Map<String, Object> settings) {
        return of(settings, WIDTH).orElse(1);
    }

    static int height(Map<String, Object> settings) {
        return of(settings, HEIGHT).orElse(1);
    }
}
