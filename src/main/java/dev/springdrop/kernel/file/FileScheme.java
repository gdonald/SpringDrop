package dev.springdrop.kernel.file;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;

/**
 * One place files are kept, named by the scheme their uris start with, such as
 * {@code public://} or {@code private://}. A module adds another, such as an
 * object store, by registering a bean of this type.
 */
public interface FileScheme {

    /** The scheme, without {@code ://}. */
    String scheme();

    void write(String path, InputStream content) throws IOException;

    InputStream read(String path) throws IOException;

    boolean exists(String path);

    void delete(String path) throws IOException;

    /** When the file at a path last changed, or nothing when there is no file there. */
    Optional<Instant> modified(String path) throws IOException;

    /** Deletes a directory and everything under it, doing nothing when there is none. */
    void deleteTree(String path) throws IOException;

    /** The address a browser fetches the file at directly, or nothing when it has to go through access checks. */
    Optional<String> directUrl(String path);
}
