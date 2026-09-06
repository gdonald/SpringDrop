package dev.springdrop.kernel.field.types;

import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldProperty;
import dev.springdrop.kernel.field.FieldSettings;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.FieldType;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.schema.ColumnType;
import dev.springdrop.kernel.validation.ConstraintSpec;
import dev.springdrop.kernel.validation.constraints.DateTimeConstraint;
import java.util.List;
import java.util.Map;

/**
 * A date, or a date and time, written in ISO-8601. The storage's
 * {@code datetime_type} setting decides which of the two a field stores, so a
 * birthday keeps no time of day while an event start does.
 */
@SpringDropPlugin(id = DateTimeFieldType.ID, type = FieldType.class)
public class DateTimeFieldType implements FieldType {

    public static final String ID = "datetime";

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
        return List.of(ConstraintSpec.on("", DateTimeConstraint.ID, Map.of(
                DateTimeConstraint.TYPE_OPTION, datetimeType(storage))));
    }

    /** Whether a field of this storage holds a date or a date and time. */
    public static String datetimeType(FieldStorageConfig storage) {
        return (String) FieldSettings.read(
                storage.settings(),
                Map.of(DateTimeConstraint.TYPE_OPTION, DateTimeConstraint.DATE_AND_TIME),
                DateTimeConstraint.TYPE_OPTION);
    }

    @Override
    public String defaultWidget() {
        return "datetime_default";
    }

    @Override
    public String defaultFormatter() {
        return "datetime_default";
    }

    @Override
    public Map<String, Object> defaultStorageSettings() {
        return Map.of(DateTimeConstraint.TYPE_OPTION, DateTimeConstraint.DATE_AND_TIME);
    }

    @Override
    public Map<String, Object> defaultInstanceSettings() {
        return Map.of();
    }
}
