package dev.springdrop.kernel.event;

/**
 * Published around an entity's lifecycle. The {@link Phase} tells listeners which
 * point in the lifecycle fired, the SpringDrop replacement for Drupal's
 * {@code hook_entity_presave}, {@code _insert}, {@code _update}, {@code _delete},
 * and {@code _load} hooks. The entity CRUD service publishes these; listeners
 * subscribe with {@code @EventListener} and order with {@code @Order}.
 */
public class EntityEvent {

    public enum Phase {
        PRESAVE,
        INSERT,
        UPDATE,
        DELETE,
        LOAD
    }

    private final Object entity;
    private final String entityType;
    private final Phase phase;

    public EntityEvent(Object entity, String entityType, Phase phase) {
        this.entity = entity;
        this.entityType = entityType;
        this.phase = phase;
    }

    public Object entity() {
        return entity;
    }

    public String entityType() {
        return entityType;
    }

    public Phase phase() {
        return phase;
    }
}
