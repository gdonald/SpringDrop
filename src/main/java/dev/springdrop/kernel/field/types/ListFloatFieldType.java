package dev.springdrop.kernel.field.types;

import dev.springdrop.kernel.field.AllowedValues;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldProperty;
import dev.springdrop.kernel.field.FieldSettings;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.FieldType;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.schema.ColumnType;
import dev.springdrop.kernel.validation.ConstraintSpec;
import dev.springdrop.kernel.validation.constraints.AllowedValuesConstraint;
import java.util.List;
import java.util.Map;

/**
 * A value chosen from a list of numbers. The values an editor may pick from live in the storage's
 * {@code allowed_values} setting, keyed by the stored value, so changing a label
 * leaves stored values untouched.
 */
@SpringDropPlugin(id = ListFloatFieldType.ID, type = FieldType.class)
public class ListFloatFieldType implements FieldType {

    public static final String ID = "list_float";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<FieldProperty> properties() {
        return List.of(FieldProperty.required(FieldProperty.VALUE, ColumnType.TEXT));
    }

    @Override
    public List<ConstraintSpec> defaultConstraints(FieldStorageConfig storage, FieldInstanceConfig instance) {
        return List.of(ConstraintSpec.on("", AllowedValuesConstraint.ID,
                Map.of("values", AllowedValues.of(storage))));
    }

    @Override
    public String defaultWidget() {
        return "options_select";
    }

    @Override
    public String defaultFormatter() {
        return "list_default";
    }

    @Override
    public Map<String, Object> defaultStorageSettings() {
        return Map.of(FieldSettings.ALLOWED_VALUES, List.of());
    }

    @Override
    public Map<String, Object> defaultInstanceSettings() {
        return Map.of();
    }
}
