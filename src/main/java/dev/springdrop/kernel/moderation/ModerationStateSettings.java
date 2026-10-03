package dev.springdrop.kernel.moderation;

/**
 * What a moderation state means for content in it: whether the content is
 * published, and whether a revision moved into it becomes the one the site
 * shows.
 */
public record ModerationStateSettings(boolean published, boolean defaultRevision) {

    /** A state that neither publishes nor replaces what the site shows, such as a draft. */
    public static final ModerationStateSettings UNPUBLISHED = new ModerationStateSettings(false, false);
}
