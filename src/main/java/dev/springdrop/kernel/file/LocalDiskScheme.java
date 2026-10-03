package dev.springdrop.kernel.file;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Files kept in a directory on local disk. A path naming anything outside the
 * directory, such as one climbing out with {@code ..}, is refused.
 */
public class LocalDiskScheme implements FileScheme {

    private final String scheme;
    private final Path root;
    private final String urlPrefix;

    /**
     * @param urlPrefix the address the directory is served at directly, or null when it is not
     */
    public LocalDiskScheme(String scheme, Path root, String urlPrefix) {
        this.scheme = scheme;
        this.root = root.toAbsolutePath().normalize();
        this.urlPrefix = urlPrefix;
    }

    @Override
    public String scheme() {
        return scheme;
    }

    public Path root() {
        return root;
    }

    /**
     * Writes to a temporary file beside the target and moves it into place, so a
     * reader never sees a file half written.
     */
    @Override
    public void write(String path, InputStream content) throws IOException {
        Path target = resolve(path);
        Files.createDirectories(target.getParent());
        Path partial = Files.createTempFile(target.getParent(), ".partial-", ".tmp");
        try {
            Files.copy(content, partial, StandardCopyOption.REPLACE_EXISTING);
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    @Override
    public InputStream read(String path) throws IOException {
        return Files.newInputStream(resolve(path));
    }

    @Override
    public boolean exists(String path) {
        return Files.isRegularFile(resolve(path));
    }

    @Override
    public void delete(String path) throws IOException {
        Files.deleteIfExists(resolve(path));
    }

    @Override
    public Optional<Instant> modified(String path) throws IOException {
        Path file = resolve(path);
        return Files.isRegularFile(file) ? Optional.of(Files.getLastModifiedTime(file).toInstant()) : Optional.empty();
    }

    @Override
    public void deleteTree(String path) throws IOException {
        Path directory = resolve(path);
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> contents = Files.walk(directory)) {
            for (Path each : contents.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(each);
            }
        }
    }

    @Override
    public Optional<String> directUrl(String path) {
        return Optional.ofNullable(urlPrefix).map(prefix -> prefix + "/" + path);
    }

    /** The file a path names under the directory, refusing one outside it. */
    Path resolve(String path) {
        Path resolved = root.resolve(path).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new IllegalArgumentException("The path " + path + " is outside the " + scheme + " files");
        }
        return resolved;
    }
}
