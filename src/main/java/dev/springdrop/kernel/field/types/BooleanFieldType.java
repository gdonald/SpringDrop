package dev.springdrop.kernel.field.types;

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
 * A yes or no value. The instance names what on and off mean to an editor, so a
 * field can read "Published" and "Unpublished" rather than true and false.
 */
@SpringDropPlugin(id = BooleanFieldType.ID, type = FieldType.class)
public class BooleanFieldType implements FieldType {

    public static final String ID = "boolean";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<FieldProperty> properties() {
        return List.of(FieldProperty.required(FieldProperty.VALUE, ColumnType.BOOLEAN));
    }

    @Override
    public List<ConstraintSpec> defaultConstraints(FieldStorageConfig storage, FieldInstanceConfig instance) {
        return List.of(ConstraintSpec.on("", AllowedValuesConstraint.ID,
                Map.of("values", List.of(true, false))));
    }

    @Override
    public String defaultWidget() {
        return "boolean_checkbox";
    }

    @Override
    public String defaultFormatter() {
        return "boolean";
    }

    @Override
    public Map<String, Object> defaultStorageSettings() {
        return Map.of();
    }

    @Override
    public Map<String, Object> defaultInstanceSettings() {
        return Map.of(FieldSettings.ON_LABEL, "On", FieldSettings.OFF_LABEL, "Off");
    }
}
