package dev.springdrop.kernel.views;

import dev.springdrop.kernel.entity.EntityData;
import java.util.Map;
import java.util.Optional;

/**
 * One result of a view: the entity listed, the entities its relationships
 * reach from it, and the values the reader gave the view's exposed form.
 */
public record ResultRow(EntityData entity, Map<String, EntityData> related, Map<String, String> input) {

    public ResultRow {
        related = Map.copyOf(related);
        input = Map.copyOf(input);
    }

    public ResultRow(EntityData entity, Map<String, EntityData> related) {
        this(entity, related, Map.of());
    }

    /** The entity a handler reads: the listed one, or one its relationship reaches, when it reaches one. */
    public Optional<EntityData> entity(String relationship) {
        return HandlerConfig.BASE.equals(relationship) ? Optional.of(entity)
                : Optional.ofNullable(related.get(relationship));
    }
}
