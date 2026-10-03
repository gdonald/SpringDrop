package dev.springdrop.kernel.node;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.layout.LayoutDisplayManager;
import dev.springdrop.kernel.schema.ColumnType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Nodes: the site's content. They come in content types, which carry the fields
 * their nodes hold, and every save keeps a revision. The entity's label is the
 * node's title.
 */
@Component
public class NodeEntityType implements EntityTypeProvider {

    public static final String ID = "node";

    public static final String TYPE_ID = "node_type";

    public static final String BUNDLE_KEY = "type";

    /** Whether the node is listed on the front page. */
    public static final String PROMOTE = "promote";

    /** Whether the node is listed before the others on the front page. */
    public static final String STICKY = "sticky";

    /** What the person saving a revision wrote about it. */
    public static final String REVISION_LOG = "revision_log";

    /** The account that saved the revision. */
    public static final String REVISION_USER = "revision_user";

    /** When the revision was saved. */
    public static final String REVISION_CREATED = "revision_created";

    /** Whether the revision is the one the site shows. */
    public static final String REVISION_DEFAULT = "revision_default";

    /** The workflow state the revision is in, for a content type under moderation. */
    public static final String MODERATION_STATE = "moderation_state";

    public static String path(Object id) {
        return "/node/" + id;
    }

    public static String editPath(Object id) {
        return path(id) + "/edit";
    }

    public static EntityType definition() {
        List<BaseFieldDefinition> baseFields = new ArrayList<>(BaseFieldDefinition.authoredContent());
        baseFields.add(BaseFieldDefinition.optional(PROMOTE, ColumnType.BOOLEAN));
        baseFields.add(BaseFieldDefinition.optional(STICKY, ColumnType.BOOLEAN));
        baseFields.add(BaseFieldDefinition.optional(REVISION_LOG, ColumnType.TEXT));
        baseFields.add(BaseFieldDefinition.optional(REVISION_USER, ColumnType.BIGINT));
        baseFields.add(BaseFieldDefinition.optional(REVISION_CREATED, ColumnType.TIMESTAMP));
        baseFields.add(BaseFieldDefinition.optional(REVISION_DEFAULT, ColumnType.BOOLEAN));
        baseFields.add(BaseFieldDefinition.optional(MODERATION_STATE, ColumnType.VARCHAR));
        baseFields.add(BaseFieldDefinition.optional(LayoutDisplayManager.OVERRIDE_FIELD, ColumnType.JSONB));
        return EntityType.content(ID, EntityData.class)
                .withBundles(BUNDLE_KEY, TYPE_ID)
                .withRevisions()
                .withTranslations()
                .withBaseFields(baseFields)
                .withAccessHandler(NodeAccessHandler.class)
                .withLinks(Map.of("canonical", "/node/{node}", "edit-form", "/node/{node}/edit"));
    }

    @Override
    public List<EntityType> entityTypes() {
        return List.of(definition(), EntityType.config(TYPE_ID, NodeType.class));
    }
}
