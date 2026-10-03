package dev.springdrop.kernel.file;

import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldProperty;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.FieldType;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.schema.ColumnType;
import dev.springdrop.kernel.validation.ConstraintSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A reference to an uploaded image, with its alternative text, its title, and
 * its width and height in pixels. Beyond a file field's limits, an image field
 * can require alternative text and bound the image's size in pixels.
 */
@SpringDropPlugin(id = ImageFieldType.ID, type = FieldType.class)
public class ImageFieldType implements FieldType {

    public static final String ID = "image";

    public static final String DEFAULT_EXTENSIONS = "png gif jpg jpeg";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<FieldProperty> properties() {
        return List.of(
                FieldProperty.required(FileItem.TARGET_ID, ColumnType.BIGINT),
                new FieldProperty(FileItem.ALT, ColumnType.VARCHAR, false),
                new FieldProperty(FileItem.TITLE, ColumnType.VARCHAR, false),
                new FieldProperty(FileItem.WIDTH, ColumnType.INTEGER, false),
                new FieldProperty(FileItem.HEIGHT, ColumnType.INTEGER, false));
    }

    @Override
    public List<ConstraintSpec> defaultConstraints(FieldStorageConfig storage, FieldInstanceConfig instance) {
        Map<String, Object> options = UploadLimits.of(storage, instance).options();
        options.put(FileItem.ALT_FIELD_REQUIRED, Boolean.TRUE.equals(instance.settings().get(FileItem.ALT_FIELD))
                && Boolean.TRUE.equals(instance.settings().get(FileItem.ALT_FIELD_REQUIRED)));
        return List.of(ConstraintSpec.on("", FileItemConstraint.ID, options));
    }

    @Override
    public String defaultWidget() {
        return "image_image";
    }

    @Override
    public String defaultFormatter() {
        return ImageFormatter.ID;
    }

    @Override
    public Map<String, Object> defaultStorageSettings() {
        return Map.of(FileItem.URI_SCHEME, FileSchemes.PUBLIC);
    }

    @Override
    public Map<String, Object> defaultInstanceSettings() {
        return Map.of(
                FileItem.FILE_EXTENSIONS, DEFAULT_EXTENSIONS,
                FileItem.MAX_FILESIZE, 0L,
                FileItem.ALT_FIELD, true,
                FileItem.ALT_FIELD_REQUIRED, true,
                FileItem.TITLE_FIELD, false,
                FileItem.MIN_RESOLUTION, "",
                FileItem.MAX_RESOLUTION, "");
    }

    @Override
    public List<FormElement> instanceSettingsForm(Map<String, Object> settings) {
        List<FormElement> elements = new ArrayList<>(FileItem.uploadLimitsForm(settings));
        elements.add(resolution(FileItem.MIN_RESOLUTION, "Smallest image", settings));
        elements.add(resolution(FileItem.MAX_RESOLUTION, "Largest image", settings));
        elements.add(FileItem.checkbox(FileItem.ALT_FIELD, "Ask for alternative text",
                settings.get(FileItem.ALT_FIELD)));
        elements.add(FileItem.checkbox(FileItem.ALT_FIELD_REQUIRED, "Require alternative text",
                settings.get(FileItem.ALT_FIELD_REQUIRED)));
        elements.add(FileItem.checkbox(FileItem.TITLE_FIELD, "Ask for a title", settings.get(FileItem.TITLE_FIELD)));
        return elements;
    }

    private static FormElement resolution(String setting, String label, Map<String, Object> settings) {
        return FormElement.of(ElementType.TEXTFIELD, FileItem.SETTINGS_PREFIX + setting)
                .label(label)
                .description("Width and height in pixels, such as 640x480. Blank for no limit.")
                .value(String.valueOf(settings.getOrDefault(setting, "")))
                .rule(ValidationRule.pattern(FileItem.RESOLUTION_PATTERN)
                        .withMessage("Write the width and height as 640x480."));
    }

    @Override
    public Map<String, Object> instanceSettingsValues(Map<String, String> submitted) {
        Map<String, Object> values = FileItem.uploadLimitsValues(submitted, DEFAULT_EXTENSIONS);
        for (String setting : List.of(FileItem.MIN_RESOLUTION, FileItem.MAX_RESOLUTION)) {
            String resolution = FileItem.submitted(submitted, setting);
            values.put(setting, resolution.matches(FileItem.RESOLUTION_PATTERN) ? resolution : "");
        }
        for (String setting : List.of(FileItem.ALT_FIELD, FileItem.ALT_FIELD_REQUIRED, FileItem.TITLE_FIELD)) {
            values.put(setting, FileItem.checked(submitted, setting));
        }
        return values;
    }
}
