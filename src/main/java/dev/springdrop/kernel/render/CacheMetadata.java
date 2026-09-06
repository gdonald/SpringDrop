package dev.springdrop.kernel.render;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * When a piece of output may be reused: the contexts it varies by, the tags that
 * invalidate it, and how long it stays valid. An empty max age means it holds
 * until one of its tags is invalidated, and a zero max age means it may not be
 * cached at all.
 *
 * <p>Merging is how cacheability bubbles: a parent is only as reusable as the
 * least reusable thing inside it, so the merged metadata carries every context
 * and tag and the shorter of the two max ages.
 */
public record CacheMetadata(Set<String> contexts, Set<String> tags, Optional<Duration> maxAge) {

    public static final CacheMetadata EMPTY =
            new CacheMetadata(Set.of(), Set.of(), Optional.empty());

    public CacheMetadata {
        // Insertion order is kept so a cache key built from these sets is stable.
        contexts = Collections.unmodifiableSet(new LinkedHashSet<>(contexts));
        tags = Collections.unmodifiableSet(new LinkedHashSet<>(tags));
    }

    public CacheMetadata withContext(String context) {
        return new CacheMetadata(plus(contexts, context), tags, maxAge);
    }

    public CacheMetadata withTag(String tag) {
        return new CacheMetadata(contexts, plus(tags, tag), maxAge);
    }

    public CacheMetadata withMaxAge(Duration newMaxAge) {
        return new CacheMetadata(contexts, tags, Optional.of(newMaxAge));
    }

    public boolean isCacheable() {
        return maxAge.map(age -> !age.isZero()).orElse(true);
    }

    public CacheMetadata merge(CacheMetadata other) {
        return new CacheMetadata(
                union(contexts, other.contexts),
                union(tags, other.tags),
                shorter(maxAge, other.maxAge));
    }

    private static Set<String> plus(Set<String> values, String value) {
        Set<String> combined = new LinkedHashSet<>(values);
        combined.add(value);
        return combined;
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> combined = new LinkedHashSet<>(first);
        combined.addAll(second);
        return combined;
    }

    private static Optional<Duration> shorter(Optional<Duration> first, Optional<Duration> second) {
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        return Optional.of(first.get().compareTo(second.get()) <= 0 ? first.get() : second.get());
    }
}
