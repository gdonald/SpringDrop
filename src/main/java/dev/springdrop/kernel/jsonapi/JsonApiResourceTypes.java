package dev.springdrop.kernel.jsonapi;

import dev.springdrop.kernel.entity.BundleManager;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityKind;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Every resource type JSON:API serves: one per bundle of each bundled content
 * entity type, and one for each unbundled content type and each config type.
 */
@Component
public class JsonApiResourceTypes {

    private final EntityTypeManager entityTypes;
    private final BundleManager bundles;

    public JsonApiResourceTypes(EntityTypeManager entityTypes, BundleManager bundles) {
        this.entityTypes = entityTypes;
        this.bundles = bundles;
    }

    /** Every resource type, by entity type and then bundle. */
    public List<JsonApiResourceType> all() {
        List<JsonApiResourceType> all = new ArrayList<>();
        for (EntityType type : entityTypes.all().stream().sorted(Comparator.comparing(EntityType::id)).toList()) {
            if (type.kind() == EntityKind.CONTENT && type.bundleEntityType() != null) {
                bundles.bundles(type.id()).stream()
                        .map(bundle -> new JsonApiResourceType(type.id(), bundle.id(), false))
                        .sorted(Comparator.comparing(JsonApiResourceType::bundle))
                        .forEach(all::add);
            } else {
                all.add(new JsonApiResourceType(type.id(), type.id(), type.kind() == EntityKind.CONFIG));
            }
        }
        return List.copyOf(all);
    }

    public Optional<JsonApiResourceType> find(String entityType, String bundle) {
        return entityTypes.find(entityType).filter(type -> type.kind() == EntityKind.CONTENT
                && type.bundleEntityType() != null
                        ? bundles.find(type.id(), bundle).isPresent()
                        : bundle.equals(type.id()))
                .map(type -> new JsonApiResourceType(type.id(), bundle, type.kind() == EntityKind.CONFIG));
    }

    /** The resource type a content entity is served as. */
    public JsonApiResourceType of(EntityData entity) {
        return new JsonApiResourceType(entity.entityType(),
                entity.bundle() == null ? entity.entityType() : entity.bundle(), false);
    }
}
