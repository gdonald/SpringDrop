package dev.springdrop.kernel.field.types;

import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldProperty;
import dev.springdrop.kernel.field.FieldSettings;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.FieldType;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.schema.ColumnType;
import dev.springdrop.kernel.validation.ConstraintSpec;
import dev.springdrop.kernel.validation.constraints.RangeConstraint;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exact numbers of a fixed precision and scale, the type for money.
 */
@SpringDropPlugin(id = DecimalFieldType.ID, type = FieldType.class)
public class DecimalFieldType implements FieldType {

    public static final String ID = "decimal";

    public static final int DEFAULT_PRECISION = 10;

    public static final int DEFAULT_SCALE = 2;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<FieldProperty> properties() {
        return List.of(FieldProperty.required(FieldProperty.VALUE, ColumnType.TEXT));
    }

    /**
     * A minimum or maximum set on the instance bounds the value; a field with
     * neither accepts any number the storage can hold.
     */
    @Override
    public List<ConstraintSpec> defaultConstraints(FieldStorageConfig storage, FieldInstanceConfig instance) {
        Map<String, Object> bounds = new LinkedHashMap<>();
        for (String bound : List.of(FieldSettings.MIN, FieldSettings.MAX)) {
            Object value = FieldSettings.read(instance.settings(), defaultInstanceSettings(), bound);
            if (value != null) {
                bounds.put(bound, value);
            }
        }
        return bounds.isEmpty() ? List.of() : List.of(ConstraintSpec.on("", RangeConstraint.ID, bounds));
    }

    @Override
    public String defaultWidget() {
        return "number";
    }

    @Override
    public String defaultFormatter() {
        return "number_decimal";
    }

    @Override
    public Map<String, Object> defaultStorageSettings() {
        return Map.of(FieldSettings.PRECISION, DEFAULT_PRECISION, FieldSettings.SCALE, DEFAULT_SCALE);
    }

    @Override
    public Map<String, Object> defaultInstanceSettings() {
        return Map.of();
    }
}
