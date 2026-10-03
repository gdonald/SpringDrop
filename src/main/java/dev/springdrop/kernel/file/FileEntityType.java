package dev.springdrop.kernel.file;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.schema.ColumnType;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Managed files: what the site keeps track of about each file it stores, where
 * it is, what it is called, what kind it is, how big it is, who added it, and
 * whether it is permanent or a temporary upload not yet put to use. The
 * entity's label is the file's name.
 */
@Component
public class FileEntityType implements EntityTypeProvider {

    public static final String ID = "file";

    /** Where the file is, as {@code <scheme>://<path>}. */
    public static final String URI = "uri";

    public static final String MIME = "filemime";

    public static final String SIZE = "filesize";

    @Override
    public List<EntityType> entityTypes() {
        return List.of(EntityType.content(ID, EntityData.class)
                .withBaseFields(List.of(
                        BaseFieldDefinition.required(URI, ColumnType.VARCHAR),
                        BaseFieldDefinition.optional(MIME, ColumnType.VARCHAR),
                        BaseFieldDefinition.optional(SIZE, ColumnType.BIGINT),
                        BaseFieldDefinition.optional(BaseFieldDefinition.OWNER, ColumnType.BIGINT),
                        BaseFieldDefinition.optional(BaseFieldDefinition.STATUS, ColumnType.BOOLEAN)
                                .withDefault(false),
                        BaseFieldDefinition.optional(BaseFieldDefinition.CREATED, ColumnType.TIMESTAMP),
                        BaseFieldDefinition.optional(BaseFieldDefinition.CHANGED, ColumnType.TIMESTAMP))));
    }
}
