package dev.springdrop.kernel.comment;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.schema.ColumnType;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Comments: what readers write about an entity, posted through one of the
 * entity's comment fields. A comment is fieldable, its label is its subject,
 * and it sits in a thread under the entity or under the comment it replies to.
 */
@Component
public class CommentEntityType implements EntityTypeProvider {

    public static final String ID = "comment";

    /** The entity type of what the comment is about. */
    public static final String HOST_TYPE = "entity_type";

    /** The id of what the comment is about. */
    public static final String HOST_ID = "entity_id";

    /** The comment field of the host the comment was posted through. */
    public static final String FIELD_NAME = "field_name";

    /** The comment this one replies to, or {@link #NO_PARENT}. */
    public static final String PARENT = "pid";

    public static final long NO_PARENT = 0L;

    /** What the comment says, as plain text. */
    public static final String BODY = "comment_body";

    /** The name someone not signed in left with the comment. */
    public static final String NAME = "name";

    /** The address someone not signed in left with the comment. */
    public static final String MAIL = "mail";

    /** The web site someone not signed in left with the comment. */
    public static final String HOMEPAGE = "homepage";

    /** The address the comment was posted from. */
    public static final String HOSTNAME = "hostname";

    /**
     * Where the comment sorts in its thread: one segment per level, each a
     * length-prefixed base-36 number, joined by dots and ended with a slash.
     */
    public static final String THREAD = "thread";

    public static String path(Object id) {
        return "/comment/" + id;
    }

    public static EntityType definition() {
        return EntityType.content(ID, EntityData.class)
                .withBaseFields(List.of(
                        BaseFieldDefinition.required(HOST_TYPE, ColumnType.VARCHAR),
                        BaseFieldDefinition.required(HOST_ID, ColumnType.BIGINT),
                        BaseFieldDefinition.required(FIELD_NAME, ColumnType.VARCHAR),
                        BaseFieldDefinition.optional(PARENT, ColumnType.BIGINT).withDefault(NO_PARENT),
                        BaseFieldDefinition.optional(BODY, ColumnType.TEXT),
                        BaseFieldDefinition.optional(BaseFieldDefinition.OWNER, ColumnType.BIGINT),
                        BaseFieldDefinition.optional(NAME, ColumnType.VARCHAR),
                        BaseFieldDefinition.optional(MAIL, ColumnType.VARCHAR),
                        BaseFieldDefinition.optional(HOMEPAGE, ColumnType.VARCHAR),
                        BaseFieldDefinition.optional(HOSTNAME, ColumnType.VARCHAR),
                        BaseFieldDefinition.optional(BaseFieldDefinition.CREATED, ColumnType.TIMESTAMP),
                        BaseFieldDefinition.optional(BaseFieldDefinition.CHANGED, ColumnType.TIMESTAMP),
                        BaseFieldDefinition.optional(BaseFieldDefinition.STATUS, ColumnType.BOOLEAN),
                        BaseFieldDefinition.optional(THREAD, ColumnType.VARCHAR)))
                .withAccessHandler(CommentAccessHandler.class)
                .withLinks(Map.of("canonical", "/comment/{comment}"));
    }

    @Override
    public List<EntityType> entityTypes() {
        return List.of(definition());
    }
}
