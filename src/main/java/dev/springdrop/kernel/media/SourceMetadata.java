package dev.springdrop.kernel.media;

/** What a media source reads from a source value: a default name, and the id of the thumbnail's managed file. */
public record SourceMetadata(String name, long thumbnail) {
}
