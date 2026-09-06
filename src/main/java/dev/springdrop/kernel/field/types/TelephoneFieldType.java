package dev.springdrop.kernel.field.types;

import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldProperty;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.FieldType;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.schema.ColumnType;
import dev.springdrop.kernel.validation.ConstraintSpec;
import dev.springdrop.kernel.validation.constraints.TelephoneConstraint;
import java.util.List;
import java.util.Map;

/**
 * A telephone number, checked when it is saved.
 */
@SpringDropPlugin(id = TelephoneFieldType.ID, type = FieldType.class)
public class TelephoneFieldType implements FieldType {

    public static final String ID = "telephone";

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
        return List.of(ConstraintSpec.on("", TelephoneConstraint.ID));
    }

    @Override
    public String defaultWidget() {
        return "telephone_default";
    }

    @Override
    public String defaultFormatter() {
        return "basic_string";
    }

    @Override
    public Map<String, Object> defaultStorageSettings() {
        return Map.of();
    }

    @Override
    public Map<String, Object> defaultInstanceSettings() {
        return Map.of();
    }
}
