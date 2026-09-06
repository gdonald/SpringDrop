package dev.springdrop.kernel.entity;

import dev.springdrop.kernel.schema.ColumnType;
import java.util.List;

/**
 * A field defined in code rather than in configuration, stored as a column of
 * the entity's base and revision tables instead of in a table of its own. The
 * keys (id, uuid, langcode) are base fields the storage always creates; a type
 * declares the rest, such as whether it is published and who owns it.
 */
public record BaseFieldDefinition(String name, ColumnType type, boolean required) {

    public static final String STATUS = "status";

    public static final String CREATED = "created";

    public static final String CHANGED = "changed";

    public static final String OWNER = "owner";

    public static BaseFieldDefinition required(String name, ColumnType type) {
        return new BaseFieldDefinition(name, type, true);
    }

    public static BaseFieldDefinition optional(String name, ColumnType type) {
        return new BaseFieldDefinition(name, type, false);
    }

    /**
     * The base fields authored content carries: whether it is published, when it
     * was created and last changed, and the id of the user who owns it.
     */
    public static List<BaseFieldDefinition> authoredContent() {
        return List.of(
                optional(STATUS, ColumnType.BOOLEAN),
                optional(CREATED, ColumnType.TIMESTAMP),
                optional(CHANGED, ColumnType.TIMESTAMP),
                optional(OWNER, ColumnType.BIGINT));
    }
}
