package dev.springdrop.kernel.media;

/**
 * A kind of media: its name, what it is for, the media source its items are
 * drawn from, and the field of the type holding each item's source value.
 * Stored as the config entity {@code media_type.<id>}, which is also the
 * bundle its media belong to.
 */
public record MediaType(String id, String label, String description, String source, String sourceField) {

    /** The name of the field holding the source value, from the type's id. */
    public static String sourceFieldFor(String typeId) {
        return "field_media_" + typeId;
    }
}
