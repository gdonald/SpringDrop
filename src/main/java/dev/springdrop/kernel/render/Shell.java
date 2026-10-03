package dev.springdrop.kernel.render;

import java.util.Map;

/**
 * A drawing with its placeholders not yet filled: the markup holding a marker
 * for each, what the drawing carries without them, the builders that fill
 * them, and how many placeholders were numbered, so ones built later are
 * numbered after them.
 */
public record Shell(String html, CacheMetadata cache, Attachments attachments, Map<String, LazyBuilder> placeholders,
        int numbered) {

    public Shell {
        placeholders = Map.copyOf(placeholders);
    }

    /** Whether every placeholder is a named one, which another request can build again. */
    public boolean reusable() {
        return placeholders.values().stream().allMatch(Placeholder.class::isInstance);
    }
}
