package dev.springdrop.kernel.file;

import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldProperty;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.FieldType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.schema.ColumnType;
import dev.springdrop.kernel.validation.ConstraintSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A reference to an uploaded file, with a description and whether the file is
 * listed when the entity is shown. Uploads are kept in the scheme the storage's
 * {@code uri_scheme} setting names, and bounded by the instance's allowed
 * extensions and largest size.
 */
@SpringDropPlugin(id = FileFieldType.ID, type = FieldType.class)
public class FileFieldType implements FieldType {

    public static final String ID = "file";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<FieldProperty> properties() {
        return List.of(
                FieldProperty.required(FileItem.TARGET_ID, ColumnType.BIGINT),
                new FieldProperty(FileItem.DISPLAY, ColumnType.BOOLEAN, false),
                new FieldProperty(FileItem.DESCRIPTION, ColumnType.VARCHAR, false));
    }

    @Override
    public List<ConstraintSpec> defaultConstraints(FieldStorageConfig storage, FieldInstanceConfig instance) {
        return List.of(ConstraintSpec.on("", FileItemConstraint.ID, UploadLimits.of(storage, instance).options()));
    }

    @Override
    public String defaultWidget() {
        return "file_generic";
    }

    @Override
    public String defaultFormatter() {
        return FileFormatter.ID;
    }

    @Override
    public Map<String, Object> defaultStorageSettings() {
        return Map.of(FileItem.URI_SCHEME, FileSchemes.PUBLIC);
    }

    @Override
    public Map<String, Object> defaultInstanceSettings() {
        return Map.of(FileItem.FILE_EXTENSIONS, "txt", FileItem.MAX_FILESIZE, 0L,
                FileItem.DESCRIPTION_FIELD, false);
    }

    @Override
    public List<FormElement> instanceSettingsForm(Map<String, Object> settings) {
        List<FormElement> elements = new ArrayList<>(FileItem.uploadLimitsForm(settings));
        elements.add(FileItem.checkbox(FileItem.DESCRIPTION_FIELD, "Ask for a description",
                settings.get(FileItem.DESCRIPTION_FIELD)));
        return elements;
    }

    @Override
    public Map<String, Object> instanceSettingsValues(Map<String, String> submitted) {
        Map<String, Object> values = FileItem.uploadLimitsValues(submitted, "txt");
        values.put(FileItem.DESCRIPTION_FIELD, FileItem.checked(submitted, FileItem.DESCRIPTION_FIELD));
        return values;
    }
}
