package dev.springdrop.kernel.comment;

import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldProperty;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.FieldType;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.schema.ColumnType;
import dev.springdrop.kernel.validation.ConstraintSpec;
import dev.springdrop.kernel.validation.constraints.AllowedValuesConstraint;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Comments on an entity. The field holds whether the entity takes comments,
 * open, closed, or hidden, and its instance settings decide how comments are
 * threaded, what someone not signed in leaves, whether a comment is previewed,
 * and how deep a thread may go.
 */
@SpringDropPlugin(id = CommentFieldType.ID, type = FieldType.class)
public class CommentFieldType implements FieldType {

    public static final String ID = "comment";

    /** The prefix every settings element is named with on the Field UI. */
    public static final String SETTINGS_PREFIX = "comment_";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<FieldProperty> properties() {
        return List.of(FieldProperty.required(FieldProperty.VALUE, ColumnType.INTEGER));
    }

    @Override
    public List<ConstraintSpec> defaultConstraints(FieldStorageConfig storage, FieldInstanceConfig instance) {
        return List.of(ConstraintSpec.on("", AllowedValuesConstraint.ID, Map.of("values",
                Arrays.stream(CommentStatus.values()).map(CommentStatus::value).toList())));
    }

    @Override
    public String defaultWidget() {
        return CommentStatusWidget.ID;
    }

    @Override
    public String defaultFormatter() {
        return CommentFormatter.ID;
    }

    @Override
    public Map<String, Object> defaultStorageSettings() {
        return Map.of();
    }

    @Override
    public Map<String, Object> defaultInstanceSettings() {
        return CommentSettings.DEFAULTS;
    }

    @Override
    public List<FormElement> instanceSettingsForm(Map<String, Object> settings) {
        CommentSettings current = CommentSettings.of(settings);
        return List.of(
                FormElement.of(ElementType.SELECT, SETTINGS_PREFIX + CommentSettings.DEFAULT_MODE)
                        .label("Threading")
                        .markRequired()
                        .value(current.defaultMode())
                        .options(List.of(
                                new SelectOption(CommentSettings.THREADED, "Replies are indented under what they reply to"),
                                new SelectOption(CommentSettings.FLAT, "Every comment in the order posted"))),
                FormElement.of(ElementType.SELECT, SETTINGS_PREFIX + CommentSettings.ANONYMOUS)
                        .label("Someone not signed in")
                        .markRequired()
                        .value(String.valueOf(current.anonymous()))
                        .options(List.of(
                                new SelectOption("0", "Leaves no contact details"),
                                new SelectOption("1", "May leave contact details"),
                                new SelectOption("2", "Must leave contact details"))),
                FormElement.of(ElementType.SELECT, SETTINGS_PREFIX + CommentSettings.PREVIEW)
                        .label("Preview")
                        .markRequired()
                        .value(String.valueOf(current.preview()))
                        .options(List.of(
                                new SelectOption("0", "Disabled"),
                                new SelectOption("1", "Optional"),
                                new SelectOption("2", "Required"))),
                FormElement.of(ElementType.NUMBER, SETTINGS_PREFIX + CommentSettings.DEPTH)
                        .label("Thread depth")
                        .description("How many levels a thread may reach. 0 for any.")
                        .value(current.depth())
                        .rule(ValidationRule.pattern("\\d{1,3}").withMessage("The depth is 0 or more.")));
    }

    /** The settings a submission gives, each choice outside its options taken from the defaults. */
    @Override
    public Map<String, Object> instanceSettingsValues(Map<String, String> submitted) {
        Map<String, Object> values = new LinkedHashMap<>();
        String mode = submitted.getOrDefault(SETTINGS_PREFIX + CommentSettings.DEFAULT_MODE, "");
        values.put(CommentSettings.DEFAULT_MODE, mode.equals(CommentSettings.FLAT) ? CommentSettings.FLAT
                : CommentSettings.THREADED);
        values.put(CommentSettings.ANONYMOUS, choice(submitted, CommentSettings.ANONYMOUS));
        values.put(CommentSettings.PREVIEW, choice(submitted, CommentSettings.PREVIEW));
        String depth = submitted.getOrDefault(SETTINGS_PREFIX + CommentSettings.DEPTH, "");
        values.put(CommentSettings.DEPTH, depth.matches("\\d{1,3}") ? Integer.parseInt(depth) : 0);
        return values;
    }

    private static int choice(Map<String, String> submitted, String setting) {
        String value = submitted.getOrDefault(SETTINGS_PREFIX + setting, "");
        return value.matches("[012]") ? Integer.parseInt(value) : (int) CommentSettings.DEFAULTS.get(setting);
    }
}
