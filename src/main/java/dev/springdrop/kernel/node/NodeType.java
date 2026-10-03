package dev.springdrop.kernel.node;

/**
 * A content type: its machine name and label, the description the admin lists
 * it with, the guidelines shown above its form, what its title field is called,
 * and the publishing defaults a new node of it starts with. Stored as the
 * config entity {@code node_type.<id>}, which is also the bundle the Field API
 * hangs the type's fields on.
 */
public record NodeType(
        String id,
        String label,
        String description,
        String help,
        String titleLabel,
        boolean published,
        boolean promoted,
        boolean sticky,
        boolean newRevision) {

    public static final String DEFAULT_TITLE_LABEL = "Title";

    /** A type whose nodes start published, promoted to the front page, and keeping revisions. */
    public static NodeType of(String id, String label) {
        return new NodeType(id, label, "", "", DEFAULT_TITLE_LABEL, true, true, false, true);
    }

    public NodeType describedAs(String newDescription) {
        return new NodeType(id, label, newDescription, help, titleLabel, published, promoted, sticky, newRevision);
    }

    public NodeType withHelp(String guidelines) {
        return new NodeType(id, label, description, guidelines, titleLabel, published, promoted, sticky, newRevision);
    }

    public NodeType withTitleLabel(String newTitleLabel) {
        return new NodeType(id, label, description, help, newTitleLabel, published, promoted, sticky, newRevision);
    }

    /** The same type with the publishing defaults a new node of it starts with. */
    public NodeType withDefaults(boolean newPublished, boolean newPromoted, boolean newSticky, boolean revision) {
        return new NodeType(id, label, description, help, titleLabel, newPublished, newPromoted, newSticky, revision);
    }
}
