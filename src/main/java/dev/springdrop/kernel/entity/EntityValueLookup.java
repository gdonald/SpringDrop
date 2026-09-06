package dev.springdrop.kernel.entity;

import dev.springdrop.kernel.validation.ValueLookup;
import org.springframework.stereotype.Component;

/**
 * Answers a constraint's storage questions against real entity storage, so a
 * reference is checked against what is stored rather than taken on trust.
 */
@Component
public class EntityValueLookup implements ValueLookup {

    private final EntityTypeManager entityTypeManager;

    public EntityValueLookup(EntityTypeManager entityTypeManager) {
        this.entityTypeManager = entityTypeManager;
    }

    @Override
    public boolean valueInUse(String propertyPath, Object value) {
        return false;
    }

    @Override
    public boolean referenceExists(String target, Object id) {
        return entityTypeManager.find(target)
                .map(type -> entityTypeManager.storageFor(type.id()).load(type, id).isPresent())
                .orElse(false);
    }
}
