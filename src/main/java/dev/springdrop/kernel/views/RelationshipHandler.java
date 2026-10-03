package dev.springdrop.kernel.views;

import dev.springdrop.kernel.entity.EntityData;
import java.util.Optional;

/**
 * Reaches from each result to another entity, such as a node's author, so the
 * view's other handlers can read and filter by that entity. A relationship
 * handler is a plugin registered with
 * {@code @SpringDropPlugin(type = RelationshipHandler.class)}.
 */
public interface RelationshipHandler extends ViewPlugin {

    /** The entity type the relationship reaches, from a result of the base entity type. */
    String targetType(String baseEntityType, HandlerConfig config);

    /** The id of the entity the relationship reaches from one result, when it reaches one. */
    Optional<Object> targetId(EntityData entity, HandlerConfig config);
}
