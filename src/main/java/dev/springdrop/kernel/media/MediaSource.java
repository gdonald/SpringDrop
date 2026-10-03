package dev.springdrop.kernel.media;

import java.io.IOException;
import java.util.Map;

/**
 * Where a media type's items come from: an uploaded image, document, audio, or
 * video file, or a video on another site. A source names the field type that
 * holds its source value and how that field is set up, and reads a value's
 * default name and thumbnail. It is a plugin registered with
 * {@code @SpringDropPlugin(type = MediaSource.class)}.
 */
public interface MediaSource {

    String id();

    String label();

    String description();

    /** The field type of the field holding the source value. */
    String sourceFieldType();

    Map<String, Object> sourceFieldStorageSettings();

    Map<String, Object> sourceFieldInstanceSettings();

    /** The formatter the source field is shown with by default. */
    String sourceFormatter();

    /**
     * The name a media item with this source value gets when it is given none,
     * and the managed image file standing for it.
     *
     * @throws MediaSourceException when the value cannot be used, with the reason
     * @throws IOException when a thumbnail cannot be stored
     */
    SourceMetadata metadata(Object sourceValue) throws IOException;
}
