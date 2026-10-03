package dev.springdrop.kernel.taxonomy;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.schema.ColumnType;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Taxonomy terms: the names content is classified by. They come in
 * vocabularies, which carry the fields their terms hold, every save keeps a
 * revision, and each term sits under a parent in its vocabulary's tree. The
 * entity's label is the term's name. A term stored without them starts
 * published, at the top of the tree, with no description and no weight.
 */
@Component
public class TaxonomyEntityType implements EntityTypeProvider {

    public static final String ID = "taxonomy_term";

    public static final String VOCABULARY_ID = "taxonomy_vocabulary";

    public static final String BUNDLE_KEY = "vid";

    public static final String DESCRIPTION = "description";

    public static final String WEIGHT = "weight";

    /** The term this one sits under, or {@link #ROOT} for a term at the top of the tree. */
    public static final String PARENT = "parent";

    public static final long ROOT = 0L;

    public static String path(Object id) {
        return "/taxonomy/term/" + id;
    }

    public static EntityType definition() {
        return EntityType.content(ID, EntityData.class)
                .withBundles(BUNDLE_KEY, VOCABULARY_ID)
                .withRevisions()
                .withBaseFields(List.of(
                        BaseFieldDefinition.optional(BaseFieldDefinition.STATUS, ColumnType.BOOLEAN).withDefault(true),
                        BaseFieldDefinition.optional(BaseFieldDefinition.CHANGED, ColumnType.TIMESTAMP),
                        BaseFieldDefinition.optional(DESCRIPTION, ColumnType.TEXT).withDefault(""),
                        BaseFieldDefinition.optional(WEIGHT, ColumnType.INTEGER).withDefault(0),
                        BaseFieldDefinition.optional(PARENT, ColumnType.BIGINT).withDefault(ROOT)))
                .withAccessHandler(TermAccessHandler.class)
                .withLinks(Map.of("canonical", "/taxonomy/term/{taxonomy_term}"));
    }

    @Override
    public List<EntityType> entityTypes() {
        return List.of(definition(), EntityType.config(VOCABULARY_ID, Vocabulary.class));
    }
}
