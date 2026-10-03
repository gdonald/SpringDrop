package dev.springdrop.kernel.cache;

import dev.springdrop.kernel.render.CacheMetadata;
import dev.springdrop.kernel.render.Shell;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Keeps whole pages in the Spring cache {@code page}, by the address asked for
 * and the cache contexts each page varies by. A page for someone not signed in
 * is kept finished. A page for someone signed in is kept as its shell, its
 * named placeholders built again for each request, and always varies by the
 * account. Each page is kept at most {@code springdrop.cache.page-max-age},
 * ten minutes unless that says otherwise, since some parts of a page, such as
 * its tabs and breadcrumb, carry no cache tags.
 */
@Component
public class PageCache {

    public static final String CACHE_NAME = "page";

    /** A finished page, with the nonce its inline scripts were given. */
    public record Finished(String html, String contentType, String nonce) {
    }

    /** A page shell, with the nonce its inline scripts were given. */
    public record Unfinished(Shell shell, String contentType, String nonce) {
    }

    private static final Set<String> SIGNED_IN_CONTEXTS = Set.of(CacheContexts.USER);

    private final VariationCache entries;
    private final Duration maxAge;

    public PageCache(CacheManager cacheManager, CacheTagInvalidator invalidator, CacheContexts contexts, Clock clock,
            @Value("${springdrop.cache.page-max-age:PT10M}") Duration maxAge) {
        this.entries = new VariationCache(cacheManager.getCache(CACHE_NAME), invalidator, contexts, clock);
        this.maxAge = maxAge;
    }

    private static List<String> keys(String kind, String address) {
        return List.of(kind, address);
    }

    public Optional<Finished> finished(String address) {
        return entries.get(keys("anonymous", address), Set.of()).map(Finished.class::cast);
    }

    public void keepFinished(String address, CacheMetadata metadata, Finished page) {
        entries.put(keys("anonymous", address), Set.of(), metadata.withMaxAge(maxAge).merge(metadata), page);
    }

    public Optional<Unfinished> unfinished(String address) {
        return entries.get(keys("dynamic", address), SIGNED_IN_CONTEXTS).map(Unfinished.class::cast);
    }

    public void keepUnfinished(String address, CacheMetadata metadata, Unfinished page) {
        entries.put(keys("dynamic", address), SIGNED_IN_CONTEXTS,
                metadata.withContext(CacheContexts.USER).withMaxAge(maxAge).merge(metadata), page);
    }

    /** Drops every kept page. */
    public void clear() {
        entries.clear();
    }
}
