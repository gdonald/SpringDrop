package dev.springdrop.kernel.access;

import dev.springdrop.kernel.render.CacheMetadata;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * An access decision together with the cacheability metadata that describes when
 * it may be reused: the contexts the decision varies by (the caller's
 * permissions, say), the tags that invalidate it, and how long it stays valid.
 * An empty max age means the decision holds until one of its tags is
 * invalidated; a zero max age means it may not be cached at all.
 *
 * <p>A decision is allowed, forbidden, or neutral. Neutral is what a rule says
 * when it has no opinion, so several modules can be asked in turn: one forbidding
 * settles it, and nothing at all means nobody allowed it.
 */
public record AccessResult(
        Decision decision,
        String reason,
        Set<String> cacheContexts,
        Set<String> cacheTags,
        Optional<Duration> maxAge) {

    /** What a rule says about a request. */
    public enum Decision {
        ALLOWED,
        FORBIDDEN,
        NEUTRAL
    }

    public AccessResult {
        // Insertion order is kept so a cache key built from these sets is stable.
        cacheContexts = Collections.unmodifiableSet(new LinkedHashSet<>(cacheContexts));
        cacheTags = Collections.unmodifiableSet(new LinkedHashSet<>(cacheTags));
    }

    public static AccessResult allow() {
        return of(Decision.ALLOWED, "");
    }

    public static AccessResult forbid() {
        return of(Decision.FORBIDDEN, "");
    }

    /** Says nothing either way, leaving the answer to whoever else is asked. */
    public static AccessResult neutral() {
        return of(Decision.NEUTRAL, "");
    }

    public static AccessResult forbid(String because) {
        return of(Decision.FORBIDDEN, because);
    }

    private static AccessResult of(Decision decision, String reason) {
        return new AccessResult(decision, reason, Set.of(), Set.of(), Optional.empty());
    }

    public boolean allowed() {
        return decision == Decision.ALLOWED;
    }

    public boolean forbidden() {
        return decision == Decision.FORBIDDEN;
    }

    public boolean neutralDecision() {
        return decision == Decision.NEUTRAL;
    }

    /** Why the decision went this way, for a message or a log line. */
    public AccessResult because(String newReason) {
        return new AccessResult(decision, newReason, cacheContexts, cacheTags, maxAge);
    }

    public AccessResult withCacheContext(String context) {
        return new AccessResult(decision, reason, plus(cacheContexts, context), cacheTags, maxAge);
    }

    public AccessResult withCacheTag(String tag) {
        return new AccessResult(decision, reason, cacheContexts, plus(cacheTags, tag), maxAge);
    }

    public AccessResult withMaxAge(Duration newMaxAge) {
        return new AccessResult(decision, reason, cacheContexts, cacheTags, Optional.of(newMaxAge));
    }

    public AccessResult uncacheable() {
        return withMaxAge(Duration.ZERO);
    }

    /** This decision's cacheability, in the form the render pipeline bubbles. */
    public CacheMetadata cacheMetadata() {
        return new CacheMetadata(cacheContexts, cacheTags, maxAge);
    }

    public boolean isCacheable() {
        return cacheMetadata().isCacheable();
    }

    /**
     * Both must agree: one forbidding settles it, both allowing allows, and
     * anything else says nothing. The cacheability of the combination is as
     * narrow as its narrowest part.
     */
    public AccessResult and(AccessResult other) {
        Decision combined;
        if (forbidden() || other.forbidden()) {
            combined = Decision.FORBIDDEN;
        } else if (allowed() && other.allowed()) {
            combined = Decision.ALLOWED;
        } else {
            combined = Decision.NEUTRAL;
        }
        return combine(combined, other);
    }

    /**
     * Either will do: one forbidding still settles it, otherwise one allowing is
     * enough. This is how several modules are asked about the same thing.
     */
    public AccessResult or(AccessResult other) {
        Decision combined;
        if (forbidden() || other.forbidden()) {
            combined = Decision.FORBIDDEN;
        } else if (allowed() || other.allowed()) {
            combined = Decision.ALLOWED;
        } else {
            combined = Decision.NEUTRAL;
        }
        return combine(combined, other);
    }

    private AccessResult combine(Decision combined, AccessResult other) {
        CacheMetadata merged = cacheMetadata().merge(other.cacheMetadata());
        return new AccessResult(
                combined,
                reason.isEmpty() ? other.reason() : reason,
                merged.contexts(),
                merged.tags(),
                merged.maxAge());
    }

    private static Set<String> plus(Set<String> values, String value) {
        Set<String> combined = new LinkedHashSet<>(values);
        combined.add(value);
        return combined;
    }

}
