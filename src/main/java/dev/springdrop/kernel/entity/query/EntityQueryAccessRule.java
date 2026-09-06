package dev.springdrop.kernel.entity.query;

import org.springframework.security.core.Authentication;

/**
 * Narrows a tagged query to what the person may see. A rule adds conditions to
 * the query rather than filtering afterwards, so a page of results is a page of
 * results the person can actually read.
 */
public interface EntityQueryAccessRule {

    /** The entity type this rule speaks for. */
    String entityTypeId();

    void narrow(EntityQuery query, Authentication authentication);
}
