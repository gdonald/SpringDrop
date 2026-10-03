package dev.springdrop.kernel.cache;

import java.util.Set;

/** Published when cache tags are invalidated, so caches kept outside the render cache can drop what they hold. */
public record CacheTagsInvalidatedEvent(Set<String> tags) {

    public CacheTagsInvalidatedEvent {
        tags = Set.copyOf(tags);
    }
}
