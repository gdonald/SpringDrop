package dev.springdrop.kernel.cache;

import dev.springdrop.kernel.render.Attachments;
import dev.springdrop.kernel.render.CacheMetadata;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Keeps rendered markup in the Spring cache {@code render}, by cache keys and
 * the values of the cache contexts the markup varies by, as a
 * {@link VariationCache} keeps it.
 */
@Component
public class RenderCache {

    public static final String CACHE_NAME = "render";

    /** Markup kept with the metadata and attachments it carries. */
    public record Cached(String html, CacheMetadata cache, Attachments attachments) {
    }

    private final VariationCache entries;

    public RenderCache(CacheManager cacheManager, CacheTagInvalidator invalidator, CacheContexts contexts,
            Clock clock) {
        this.entries = new VariationCache(cacheManager.getCache(CACHE_NAME), invalidator, contexts, clock);
    }

    /** The markup kept under the keys for the current values of the contexts, while it is still valid. */
    public Optional<Cached> get(List<String> keys, Set<String> knownContexts) {
        return entries.get(keys, knownContexts).map(Cached.class::cast);
    }

    /** Keeps the markup under the keys, unless its metadata says it may not be cached. */
    public void put(List<String> keys, Set<String> knownContexts, Cached cached) {
        entries.put(keys, knownContexts, cached.cache(), cached);
    }

    /** Drops everything kept. */
    public void clear() {
        entries.clear();
    }
}
