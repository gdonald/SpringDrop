package dev.springdrop.kernel.schema;

import org.jooq.DataType;
import org.jooq.impl.SQLDataType;

/**
 * The column types the dynamic schema layer supports, each mapped to a jOOQ
 * data type. This is the typed vocabulary the Field API builds its storage on,
 * the SpringDrop analogue of Drupal's Schema API column types.
 */
public enum ColumnType {

    TEXT(SQLDataType.CLOB),
    VARCHAR(SQLDataType.VARCHAR),
    INTEGER(SQLDataType.INTEGER),
    BIGINT(SQLDataType.BIGINT),
    BOOLEAN(SQLDataType.BOOLEAN),
    TIMESTAMP(SQLDataType.TIMESTAMPWITHTIMEZONE),
    UUID(SQLDataType.UUID),
    JSONB(SQLDataType.JSONB);

    private final DataType<?> dataType;

    ColumnType(DataType<?> dataType) {
        this.dataType = dataType;
    }

    DataType<?> dataType() {
        return dataType;
    }
}
