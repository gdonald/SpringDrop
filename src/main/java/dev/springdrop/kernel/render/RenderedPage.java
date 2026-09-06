package dev.springdrop.kernel.render;

/**
 * A rendered tree: the markup, the cacheability bubbled up from every part of
 * it, and everything those parts asked the page to carry.
 */
public record RenderedPage(String html, CacheMetadata cache, Attachments attachments) {
}
