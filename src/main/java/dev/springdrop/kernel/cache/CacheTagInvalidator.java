package dev.springdrop.kernel.cache;

import dev.springdrop.kernel.config.ConfigChangedEvent;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.event.EntityEvent;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Invalidates cache tags. Each tag has a count of the times it was
 * invalidated, and a cached entry keeps the checksum of its tags' counts from
 * when it was stored: once any of them is invalidated the checksum no longer
 * matches and the entry is stale. Saving or deleting an entity invalidates its
 * own tag and its type's list tag, and saving or deleting a config object
 * invalidates its config tag. The counts are kept in memory, so another
 * instance of the site keeps its own.
 */
@Component
public class CacheTagInvalidator {

    private final Map<String, Long> invalidations = new ConcurrentHashMap<>();
    private final ApplicationEventPublisher events;

    public CacheTagInvalidator(ApplicationEventPublisher events) {
        this.events = events;
    }

    public void invalidate(Collection<String> tags) {
        Set<String> invalidated = new LinkedHashSet<>(tags);
        invalidated.forEach(tag -> invalidations.merge(tag, 1L, Long::sum));
        events.publishEvent(new CacheTagsInvalidatedEvent(invalidated));
    }

    /** The checksum of the tags' invalidation counts, which grows whenever any of them is invalidated. */
    public long checksum(Collection<String> tags) {
        return tags.stream().distinct().mapToLong(tag -> invalidations.getOrDefault(tag, 0L)).sum();
    }

    @EventListener
    void entityChanged(EntityEvent event) {
        boolean changed = event.phase() == EntityEvent.Phase.INSERT || event.phase() == EntityEvent.Phase.UPDATE
                || event.phase() == EntityEvent.Phase.DELETE;
        if (changed && event.entity() instanceof EntityData entity) {
            invalidate(List.of(CacheTags.entity(event.entityType(), entity.id()), CacheTags.list(event.entityType())));
        }
    }

    @EventListener
    void configChanged(ConfigChangedEvent event) {
        invalidate(List.of(CacheTags.config(event.name())));
    }
}
