package dev.springdrop.kernel.field;

import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.field.types.EntityReferenceFieldType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Loads the entities a reference field points at. References are stored as ids
 * and resolved when asked for, so loading an entity does not drag its whole
 * reference graph along with it.
 */
@Component
public class EntityReferenceResolver {

    private final FieldConfigManager fieldConfigManager;
    private final EntityCrudService entities;

    public EntityReferenceResolver(FieldConfigManager fieldConfigManager, EntityCrudService entities) {
        this.fieldConfigManager = fieldConfigManager;
        this.entities = entities;
    }

    /** The entity a single-valued reference field points at. */
    public Optional<EntityData> target(EntityData entity, String fieldName) {
        List<EntityData> targets = targets(entity, fieldName);
        return targets.isEmpty() ? Optional.empty() : Optional.of(targets.getFirst());
    }

    /** The entities a reference field points at, in the order the field holds them. */
    public List<EntityData> targets(EntityData entity, String fieldName) {
        Object value = entity.fields().get(fieldName);
        if (value == null) {
            return List.of();
        }

        String targetType = fieldConfigManager.findStorage(entity.entityType(), fieldName)
                .map(EntityReferenceFieldType::targetType)
                .orElse("");
        if (targetType.isEmpty()) {
            return List.of();
        }

        List<Object> ids = (value instanceof List<?> values) ? List.copyOf(values) : List.of(value);
        List<EntityData> targets = new ArrayList<>();
        for (Object id : ids) {
            entities.load(targetType, id).ifPresent(targets::add);
        }
        return List.copyOf(targets);
    }
}
