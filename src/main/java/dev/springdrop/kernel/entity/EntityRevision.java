package dev.springdrop.kernel.entity;

import java.util.Map;

/** One revision of an entity as its revision row holds it: its id, the label it had, and its base field values. */
public record EntityRevision(long revisionId, String label, Map<String, Object> baseValues) {

    public EntityRevision {
        baseValues = Map.copyOf(baseValues);
    }
}
