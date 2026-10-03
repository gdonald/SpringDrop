package dev.springdrop.kernel.views;

import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.form.ValidationRule;
import java.util.List;
import java.util.Map;

/** The settings elements Views plugins share, and reading a submission of them back. */
public final class Settings {

    /** What a whole number setting is written as. */
    public static final String WHOLE_NUMBER = "\\d{1,6}";

    private Settings() {
    }

    public static FormElement text(String prefix, String name, String label, Map<String, Object> settings) {
        return FormElement.of(ElementType.TEXTFIELD, prefix + name).label(label)
                .value(String.valueOf(settings.getOrDefault(name, ""))).rule(ValidationRule.maxLength(255));
    }

    public static FormElement number(String prefix, String name, String label, Map<String, Object> settings,
            int fallback) {
        return FormElement.of(ElementType.NUMBER, prefix + name).label(label)
                .value(String.valueOf(settings.getOrDefault(name, fallback)))
                .rule(ValidationRule.pattern(WHOLE_NUMBER).withMessage("Use a whole number."));
    }

    public static FormElement checkbox(String prefix, String name, String label, Map<String, Object> settings,
            boolean fallback) {
        Object value = settings.getOrDefault(name, fallback);
        return FormElement.of(ElementType.CHECKBOX, prefix + name).label(label)
                .value(Boolean.TRUE.equals(value) || "true".equals(value));
    }

    public static FormElement select(String prefix, String name, String label, Map<String, Object> settings,
            List<SelectOption> options) {
        return FormElement.of(ElementType.SELECT, prefix + name).label(label)
                .value(String.valueOf(settings.getOrDefault(name, options.getFirst().value()))).options(options);
    }

    public static String submitted(String prefix, String name, Map<String, String> submitted) {
        return submitted.getOrDefault(prefix + name, "").strip();
    }

    public static boolean ticked(String prefix, String name, Map<String, String> submitted) {
        return FormRenderer.CHECKED_VALUE.equals(submitted.get(prefix + name));
    }

    /** A submitted choice when it is one of the values, or the first value. */
    public static String chosen(String prefix, String name, Map<String, String> submitted, List<String> values) {
        String value = submitted(prefix, name, submitted);
        return values.contains(value) ? value : values.getFirst();
    }

    public static int whole(String prefix, String name, Map<String, String> submitted, int fallback) {
        String value = submitted(prefix, name, submitted);
        return value.matches("\\d{1,6}") ? Integer.parseInt(value) : fallback;
    }
}
