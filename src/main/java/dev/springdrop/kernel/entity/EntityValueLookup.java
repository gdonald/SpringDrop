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

    /**
     * Whether the target exists. A content entity is numbered, so a reference
     * to one that is not a whole number points at nothing.
     */
    @Override
    public boolean referenceExists(String target, Object id) {
        return entityTypeManager.find(target)
                .filter(type -> type.kind() != EntityKind.CONTENT || String.valueOf(id).matches("\\d{1,18}"))
                .map(type -> entityTypeManager.storageFor(type.id()).load(type,
                        (type.kind() == EntityKind.CONTENT) ? Long.valueOf(String.valueOf(id)) : id).isPresent())
                .orElse(false);
    }
}
