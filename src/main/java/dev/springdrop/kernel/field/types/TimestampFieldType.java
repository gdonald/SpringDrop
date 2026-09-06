package dev.springdrop.kernel.field.types;

import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldProperty;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.FieldType;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.schema.ColumnType;
import dev.springdrop.kernel.validation.ConstraintSpec;
import java.util.List;
import java.util.Map;

/**
 * A moment stored as seconds since the epoch, the type behind created and
 * changed times. It carries no timezone of its own; the site's timezone decides
 * how it is shown.
 */
@SpringDropPlugin(id = TimestampFieldType.ID, type = FieldType.class)
public class TimestampFieldType implements FieldType {

    public static final String ID = "timestamp";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<FieldProperty> properties() {
        return List.of(FieldProperty.required(FieldProperty.VALUE, ColumnType.BIGINT));
    }

    @Override
    public List<ConstraintSpec> defaultConstraints(FieldStorageConfig storage, FieldInstanceConfig instance) {
        return List.of();
    }

    @Override
    public String defaultWidget() {
        return "datetime_timestamp";
    }

    @Override
    public String defaultFormatter() {
        return "timestamp";
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
