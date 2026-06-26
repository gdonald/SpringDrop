package dev.springdrop.kernel.schema;

import org.jooq.DataType;

/**
 * Describes one column for the dynamic schema layer: a name, a {@link ColumnType},
 * and whether it is nullable.
 */
public record ColumnSpec(String name, ColumnType type, boolean nullable) {

    public static ColumnSpec required(String name, ColumnType type) {
        return new ColumnSpec(name, type, false);
    }

    public static ColumnSpec optional(String name, ColumnType type) {
        return new ColumnSpec(name, type, true);
    }

    DataType<?> dataType() {
        return type.dataType().nullable(nullable);
    }
}
