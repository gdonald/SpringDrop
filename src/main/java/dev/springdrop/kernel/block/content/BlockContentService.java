package dev.springdrop.kernel.block.content;

import dev.springdrop.kernel.entity.BundleDefinition;
import dev.springdrop.kernel.entity.BundleManager;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.entity.query.Sort;
import dev.springdrop.kernel.field.display.ViewDisplayConfig;
import dev.springdrop.kernel.field.display.ViewDisplayManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** The custom blocks in the library and the block types they come in. */
@Component
public class BlockContentService {

    private final EntityCrudService entities;
    private final EntityQueryExecutor queries;
    private final EntityTypeManager entityTypeManager;
    private final BundleManager bundles;
    private final ViewDisplayManager displays;

    public BlockContentService(
            EntityCrudService entities,
            EntityQueryExecutor queries,
            EntityTypeManager entityTypeManager,
            BundleManager bundles,
            ViewDisplayManager displays) {
        this.entities = entities;
        this.queries = queries;
        this.entityTypeManager = entityTypeManager;
        this.bundles = bundles;
        this.displays = displays;
    }

    /** Creates the tables custom blocks are stored in, the step install runs. */
    public void install() {
        entityTypeManager.installStorage(BlockContentEntityType.ID);
    }

    public void saveType(BundleDefinition type) {
        bundles.save(BlockContentEntityType.ID, type);
    }

    public Optional<BundleDefinition> findType(String id) {
        return bundles.find(BlockContentEntityType.ID, id);
    }

    public void deleteType(String id) {
        bundles.delete(BlockContentEntityType.ID, id);
    }

    public List<BundleDefinition> types() {
        return bundles.bundles(BlockContentEntityType.ID);
    }

    /** Stores a custom block, creating it when it has no id and starting a new revision otherwise. */
    public EntityData save(EntityData block) {
        return entities.save(block);
    }

    public Optional<EntityData> find(long id) {
        return entities.load(BlockContentEntityType.ID, id);
    }

    public void delete(long id) {
        entities.delete(BlockContentEntityType.ID, id);
    }

    /** Every custom block in the library, listed by description. */
    public List<EntityData> all() {
        List<EntityData> blocks = new ArrayList<>();
        for (Object id : queries.query(BlockContentEntityType.ID).sort(Sort.ascending("label")).ids()) {
            find(((Number) id).longValue()).ifPresent(blocks::add);
        }
        return blocks;
    }

    /** Whether any custom block is of this type, which keeps the type from being deleted. */
    public boolean inUse(String typeId) {
        return all().stream().anyMatch(block -> block.bundle().equals(typeId));
    }

    /** The block's fields rendered for reading, the way its type's full display lays them out. */
    public String render(EntityData block) {
        return displays.render(
                BlockContentEntityType.ID, block.bundle(), ViewDisplayConfig.FULL_MODE, block.fields());
    }

    /** The tag invalidating what a custom block rendered. */
    public static String cacheTag(long id) {
        return BlockContentEntityType.ID + ":" + id;
    }
}
