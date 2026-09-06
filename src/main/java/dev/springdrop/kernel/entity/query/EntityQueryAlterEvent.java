package dev.springdrop.kernel.entity.query;

import dev.springdrop.kernel.event.AlterEvent;

/**
 * Published before a tagged entity query runs, so the access system and modules
 * can add conditions to it. Listeners mutate the query in place, the way
 * Drupal's {@code hook_query_alter} mutates a query before execution.
 */
public class EntityQueryAlterEvent extends AlterEvent<EntityQuery> {

    public EntityQueryAlterEvent(EntityQuery query) {
        super(query);
    }
}
