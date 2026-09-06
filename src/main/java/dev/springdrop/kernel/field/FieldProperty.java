package dev.springdrop.kernel.field;

import dev.springdrop.kernel.schema.ColumnType;

/**
 * One property of a field value. A single-property type stores its value on its
 * own; a multi-property type stores a map keyed by property name, so formatted
 * text keeps its text and its format together in one value.
 */
public record FieldProperty(String name, ColumnType type, boolean required) {

    /** The property a single-property type stores its value under. */
    public static final String VALUE = "value";

    public static FieldProperty required(String name, ColumnType type) {
        return new FieldProperty(name, type, true);
    }
}
