package dev.springdrop.kernel.cache;

import dev.springdrop.kernel.render.CacheMetadata;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.cache.Cache;

/**
 * Keeps values in a Spring cache by keys and the values of the cache contexts
 * they vary by, each valid until one of its tags is invalidated or its max age
 * passes. A value that may not be cached is not kept.
 *
 * <p>A value can vary by more contexts than the caller knows before making it.
 * Then the entry under the contexts the caller knew holds a redirect naming
 * every context, and the value is kept under those.
 */
public class VariationCache {

    private record Entry(Object value, Set<String> tags, long checksum, Optional<Instant> expires) {
    }

    private record Redirect(Set<String> contexts) {
    }

    private final Cache cache;
    private final CacheTagInvalidator invalidator;
    private final CacheContexts contexts;
    private final Clock clock;

    public VariationCache(Cache cache, CacheTagInvalidator invalidator, CacheContexts contexts, Clock clock) {
        this.cache = cache;
        this.invalidator = invalidator;
        this.contexts = contexts;
        this.clock = clock;
    }

    private String key(List<String> keys, Collection<String> varyBy) {
        return String.join(":", keys) + contexts.values(varyBy);
    }

    /** The value kept under the keys for the current values of the contexts, while it is still valid. */
    public Optional<Object> get(List<String> keys, Set<String> knownContexts) {
        String key = key(keys, knownContexts);
        Object found = cache.get(key, Object.class);
        if (found instanceof Redirect redirect) {
            key = key(keys, redirect.contexts());
            found = cache.get(key, Object.class);
        }
        if (!(found instanceof Entry entry)) {
            return Optional.empty();
        }
        boolean expired = entry.expires().map(expires -> !clock.instant().isBefore(expires)).orElse(false);
        if (expired || entry.checksum() != invalidator.checksum(entry.tags())) {
            cache.evict(key);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    /** Keeps the value under the keys, unless its metadata says it may not be cached. */
    public void put(List<String> keys, Set<String> knownContexts, CacheMetadata metadata, Object value) {
        if (!metadata.isCacheable()) {
            return;
        }
        if (!knownContexts.containsAll(metadata.contexts())) {
            cache.put(key(keys, knownContexts), new Redirect(metadata.contexts()));
        }
        cache.put(key(keys, metadata.contexts()), new Entry(value, metadata.tags(),
                invalidator.checksum(metadata.tags()), metadata.maxAge().map(age -> clock.instant().plus(age))));
    }

    /** Drops everything kept. */
    public void clear() {
        cache.clear();
    }
}
