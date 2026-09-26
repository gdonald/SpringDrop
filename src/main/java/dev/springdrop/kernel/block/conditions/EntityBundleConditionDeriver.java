package dev.springdrop.kernel.block.conditions;

import dev.springdrop.kernel.block.VisibilityCondition;
import dev.springdrop.kernel.entity.BundleManager;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.plugin.DerivablePlugin;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Derives a bundle condition for every entity type that has bundles, as
 * {@code entity_bundle:<entity type>}. The one for nodes is the content type
 * condition.
 */
@SpringDropPlugin(id = EntityBundleConditionDeriver.ID, type = VisibilityCondition.class)
public class EntityBundleConditionDeriver implements DerivablePlugin<VisibilityCondition> {

    public static final String ID = "entity_bundle";

    private final EntityTypeManager entityTypes;
    private final BundleManager bundles;

    public EntityBundleConditionDeriver(EntityTypeManager entityTypes, BundleManager bundles) {
        this.entityTypes = entityTypes;
        this.bundles = bundles;
    }

    @Override
    public Map<String, VisibilityCondition> derivatives() {
        Map<String, VisibilityCondition> conditions = new LinkedHashMap<>();
        for (EntityType type : entityTypes.all()) {
            if (type.bundleEntityType() != null) {
                conditions.put(type.id(), new EntityBundleCondition(type.id(), bundles));
            }
        }
        return conditions;
    }
}
