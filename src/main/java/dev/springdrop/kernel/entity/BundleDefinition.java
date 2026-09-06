package dev.springdrop.kernel.entity;

/**
 * One bundle of a content entity type, such as a node type: its machine name and
 * the label an editor sees. A bundle is stored as a config entity, so bundles
 * ship and export with a site's configuration. The fields entities of the bundle
 * carry come from the field instances attached to it.
 */
public record BundleDefinition(String id, String label) {
}
