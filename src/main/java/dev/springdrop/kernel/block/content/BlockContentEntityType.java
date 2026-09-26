package dev.springdrop.kernel.block.content;

import dev.springdrop.kernel.entity.BundleDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Custom blocks: reusable pieces of content written in the block library and
 * placed like any other block. They come in block types, which carry the fields
 * their blocks hold, and every save keeps a revision. The entity's label is the
 * block description the library lists it by.
 */
@Component
public class BlockContentEntityType implements EntityTypeProvider {

    public static final String ID = "block_content";

    public static final String TYPE_ID = "block_content_type";

    @Override
    public List<EntityType> entityTypes() {
        return List.of(
                EntityType.content(ID, EntityData.class)
                        .withBundles("type", TYPE_ID)
                        .withRevisions(),
                EntityType.config(TYPE_ID, BundleDefinition.class));
    }
}
