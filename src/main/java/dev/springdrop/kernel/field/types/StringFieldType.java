package dev.springdrop.kernel.field.types;

import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldProperty;
import dev.springdrop.kernel.field.FieldSettings;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.FieldType;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.schema.ColumnType;
import dev.springdrop.kernel.validation.ConstraintSpec;
import dev.springdrop.kernel.validation.constraints.LengthConstraint;
import java.util.List;
import java.util.Map;

/**
 * Single-line text with a maximum length, the type behind a title or a name.
 */
@SpringDropPlugin(id = StringFieldType.ID, type = FieldType.class)
public class StringFieldType implements FieldType {

    public static final String ID = "string";

    public static final int DEFAULT_MAX_LENGTH = 255;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<FieldProperty> properties() {
        return List.of(FieldProperty.required(FieldProperty.VALUE, ColumnType.VARCHAR));
    }

    @Override
    public List<ConstraintSpec> defaultConstraints(FieldStorageConfig storage, FieldInstanceConfig instance) {
        Number maxLength = FieldSettings.number(
                storage.settings(), defaultStorageSettings(), FieldSettings.MAX_LENGTH);
        return List.of(ConstraintSpec.on("", LengthConstraint.ID, Map.of("max", maxLength)));
    }

    @Override
    public String defaultWidget() {
        return "string_textfield";
    }

    @Override
    public String defaultFormatter() {
        return "string";
    }

    @Override
    public Map<String, Object> defaultStorageSettings() {
        return Map.of(FieldSettings.MAX_LENGTH, DEFAULT_MAX_LENGTH);
    }

    @Override
    public Map<String, Object> defaultInstanceSettings() {
        return Map.of();
    }
}
