package dev.springdrop.kernel.render;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Something to be rendered: which Thymeleaf fragment draws it, the data and
 * attributes that fragment reads, the children nested inside it, and the
 * cacheability and attachments it contributes to the page.
 *
 * <p>A lazy renderable carries a builder instead of a fragment. It renders as a
 * placeholder, and the builder runs once the shell around it is done, so a page
 * that is otherwise cacheable is not held back by the one part that is not.
 */
public record Renderable(
        String template,
        String type,
        Map<String, Object> data,
        Map<String, String> attributes,
        List<Renderable> children,
        CacheMetadata cache,
        Attachments attachments,
        Optional<LazyBuilder> lazyBuilder) {

    public static final String CORE_TEMPLATE = "render/elements";

    public Renderable {
        data = Map.copyOf(data);
        attributes = Map.copyOf(attributes);
        children = List.copyOf(children);
    }

    /** A renderable drawn by a fragment of the core elements template. */
    public static Renderable of(String type) {
        return of(CORE_TEMPLATE, type);
    }

    public static Renderable of(String template, String type) {
        return new Renderable(template, type, Map.of(), Map.of(), List.of(),
                CacheMetadata.EMPTY, Attachments.NONE, Optional.empty());
    }

    /** A placeholder filled in after the shell around it has rendered. */
    public static Renderable lazy(LazyBuilder builder) {
        return new Renderable("", "", Map.of(), Map.of(), List.of(),
                CacheMetadata.EMPTY, Attachments.NONE, Optional.of(builder));
    }

    public Renderable with(String key, Object value) {
        Map<String, Object> combined = new LinkedHashMap<>(data);
        combined.put(key, value);
        return new Renderable(template, type, combined, attributes, children,
                cache, attachments, lazyBuilder);
    }

    public Renderable attribute(String name, String value) {
        Map<String, String> combined = new LinkedHashMap<>(attributes);
        combined.put(name, value);
        return new Renderable(template, type, data, combined, children,
                cache, attachments, lazyBuilder);
    }

    public Renderable child(Renderable nested) {
        List<Renderable> combined = new ArrayList<>(children);
        combined.add(nested);
        return new Renderable(template, type, data, attributes, combined,
                cache, attachments, lazyBuilder);
    }

    public Renderable cacheTag(String tag) {
        return withCache(cache.withTag(tag));
    }

    public Renderable cacheContext(String context) {
        return withCache(cache.withContext(context));
    }

    public Renderable maxAge(Duration age) {
        return withCache(cache.withMaxAge(age));
    }

    public Renderable styleSheet(String href) {
        return withAttachments(attachments.withStyleSheet(href));
    }

    public Renderable script(String src) {
        return withAttachments(attachments.withScript(src));
    }

    public Renderable headTag(String markup) {
        return withAttachments(attachments.withHeadTag(markup));
    }

    private Renderable withCache(CacheMetadata updated) {
        return new Renderable(template, type, data, attributes, children,
                updated, attachments, lazyBuilder);
    }

    private Renderable withAttachments(Attachments updated) {
        return new Renderable(template, type, data, attributes, children,
                cache, updated, lazyBuilder);
    }
}
