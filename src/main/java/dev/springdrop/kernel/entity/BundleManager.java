package dev.springdrop.kernel.entity;

import dev.springdrop.kernel.config.ConfigStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The bundles of a content entity type. Each bundle is a config entity of the
 * type's bundle entity type; the fields its entities carry come from the field
 * instances attached to it.
 */
@Component
public class BundleManager {

    private static final String LABEL_KEY = "label";

    private final EntityTypeManager entityTypeManager;
    private final ConfigStore configStore;

    public BundleManager(EntityTypeManager entityTypeManager, ConfigStore configStore) {
        this.entityTypeManager = entityTypeManager;
        this.configStore = configStore;
    }

    public void save(String contentTypeId, BundleDefinition bundle) {
        EntityType bundleType = bundleTypeOf(contentTypeId);
        entityTypeManager.storageFor(bundleType.id())
                .save(bundleType, bundle.id(), Map.of(LABEL_KEY, bundle.label()));
    }

    public void delete(String contentTypeId, String bundleId) {
        EntityType bundleType = bundleTypeOf(contentTypeId);
        entityTypeManager.storageFor(bundleType.id()).delete(bundleType, bundleId);
    }

    public Optional<BundleDefinition> find(String contentTypeId, String bundleId) {
        EntityType bundleType = bundleTypeOf(contentTypeId);
        return entityTypeManager.storageFor(bundleType.id())
                .load(bundleType, bundleId)
                .map(values -> toBundle(bundleId, values));
    }

    public List<BundleDefinition> bundles(String contentTypeId) {
        EntityType bundleType = bundleTypeOf(contentTypeId);
        List<BundleDefinition> bundles = new ArrayList<>();
        for (String name : configStore.listNames(bundleType.id())) {
            String bundleId = name.substring(bundleType.id().length() + 1);
            find(contentTypeId, bundleId).ifPresent(bundles::add);
        }
        return List.copyOf(bundles);
    }

    private EntityType bundleTypeOf(String contentTypeId) {
        EntityType contentType = entityTypeManager.require(contentTypeId);
        if (contentType.bundleEntityType() == null) {
            throw new IllegalArgumentException("Entity type '" + contentTypeId + "' does not have bundles");
        }
        return entityTypeManager.require(contentType.bundleEntityType());
    }

    private static BundleDefinition toBundle(String bundleId, Map<String, Object> values) {
        return new BundleDefinition(bundleId, (String) values.get(LABEL_KEY));
    }
}
