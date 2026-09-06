package dev.springdrop.kernel.field.types;

import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldProperty;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.FieldType;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.schema.ColumnType;
import dev.springdrop.kernel.validation.ConstraintSpec;
import dev.springdrop.kernel.validation.constraints.DateRangeConstraint;
import dev.springdrop.kernel.validation.constraints.DateTimeConstraint;
import java.util.List;
import java.util.Map;

/**
 * A span with a start and an end, both written in ISO-8601. The storage's
 * {@code datetime_type} setting decides whether the two ends are dates or dates
 * and times, and a range whose end comes before its start is rejected.
 */
@SpringDropPlugin(id = DateRangeFieldType.ID, type = FieldType.class)
public class DateRangeFieldType implements FieldType {

    public static final String ID = "daterange";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<FieldProperty> properties() {
        return List.of(
                FieldProperty.required(DateRangeConstraint.START, ColumnType.VARCHAR),
                FieldProperty.required(DateRangeConstraint.END, ColumnType.VARCHAR));
    }

    @Override
    public List<ConstraintSpec> defaultConstraints(FieldStorageConfig storage, FieldInstanceConfig instance) {
        return List.of(ConstraintSpec.on("", DateRangeConstraint.ID, Map.of(
                DateTimeConstraint.TYPE_OPTION, DateTimeFieldType.datetimeType(storage))));
    }

    @Override
    public String defaultWidget() {
        return "daterange_default";
    }

    @Override
    public String defaultFormatter() {
        return "daterange_default";
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
