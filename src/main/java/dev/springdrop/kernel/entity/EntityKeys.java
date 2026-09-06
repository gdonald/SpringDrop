package dev.springdrop.kernel.entity;

/**
 * The columns that carry an entity's identity. A null key means the entity type
 * does not have that concept: an unbundled type has no bundle key, and a type
 * without revisions has no revision key.
 */
public record EntityKeys(
        String id,
        String uuid,
        String bundle,
        String label,
        String langcode,
        String revision) {

    public static EntityKeys content() {
        return new EntityKeys("id", "uuid", null, "label", "langcode", null);
    }

    public static EntityKeys config() {
        return new EntityKeys("id", "uuid", null, "label", null, null);
    }

    public EntityKeys withBundle(String bundleKey) {
        return new EntityKeys(id, uuid, bundleKey, label, langcode, revision);
    }

    public EntityKeys withRevision(String revisionKey) {
        return new EntityKeys(id, uuid, bundle, label, langcode, revisionKey);
    }
}
