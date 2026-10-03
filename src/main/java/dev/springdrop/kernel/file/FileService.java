package dev.springdrop.kernel.file;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLConnection;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Storing files and keeping track of them. A stored file starts temporary, an
 * upload not yet put to use, and becomes permanent once something uses it.
 *
 * <p>A file is kept under a directory for the month it arrived, under a name
 * made safe for a path: letters, digits, dots, dashes, and underscores, with a
 * number added when the name is taken. A name ending in an extension a web
 * server might run, or a browser render as a page, gets {@code .txt} added, so
 * the file is only ever served as text. Its kind is worked out from its name,
 * never from what the sender claimed.
 */
@Component
public class FileService {

    /** Extensions a server might run, or a browser render as a page. */
    public static final Set<String> DANGEROUS_EXTENSIONS = Set.of(
            "php", "phar", "phtml", "pl", "py", "cgi", "asp", "aspx", "jsp", "js", "mjs", "sh", "bash", "exe",
            "com", "bat", "htm", "html", "shtml", "xhtml", "svg");

    /** Where private files are served, through access checks. */
    public static final String PRIVATE_URL_PREFIX = "/system/files";

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    private final EntityCrudService entities;
    private final EntityTypeManager entityTypeManager;
    private final EntityQueryExecutor queries;
    private final FileSchemes.Registry schemes;
    private final Clock clock;

    public FileService(EntityCrudService entities, EntityTypeManager entityTypeManager, EntityQueryExecutor queries,
            FileSchemes.Registry schemes, Clock clock) {
        this.entities = entities;
        this.entityTypeManager = entityTypeManager;
        this.queries = queries;
        this.schemes = schemes;
        this.clock = clock;
    }

    /** Creates the tables managed files are stored in, as the application starts. */
    @EventListener(ApplicationReadyEvent.class)
    public void install() {
        entityTypeManager.installStorage(FileEntityType.ID);
    }

    /** Stores a file under a scheme, as a temporary file owned by the given account. */
    public ManagedFile store(InputStream content, String filename, long size, String scheme, long owner)
            throws IOException {
        FileScheme target = scheme(scheme);
        String name = safeName(filename);
        String directory = MONTH.format(OffsetDateTime.now(clock));
        String path = directory + "/" + name;
        for (int suffix = 1; target.exists(path); suffix++) {
            path = directory + "/" + numbered(name, suffix);
        }
        target.write(path, content);

        OffsetDateTime now = OffsetDateTime.now(clock);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(FileEntityType.URI, scheme + "://" + path);
        fields.put(FileEntityType.MIME, Optional.ofNullable(URLConnection.guessContentTypeFromName(name))
                .orElse("application/octet-stream"));
        fields.put(FileEntityType.SIZE, size);
        fields.put(BaseFieldDefinition.OWNER, owner);
        fields.put(BaseFieldDefinition.STATUS, false);
        fields.put(BaseFieldDefinition.CREATED, now);
        fields.put(BaseFieldDefinition.CHANGED, now);
        return ManagedFile.of(entities.save(EntityData.of(FileEntityType.ID, null, null,
                path.substring(path.lastIndexOf('/') + 1), fields)));
    }

    /** Stores content the site already holds, such as an image it drew, as a temporary file. */
    public ManagedFile store(byte[] content, String filename, String scheme, long owner) throws IOException {
        return store(new ByteArrayInputStream(content), filename, content.length, scheme, owner);
    }

    public Optional<ManagedFile> find(long id) {
        return entities.load(FileEntityType.ID, id).map(ManagedFile::of);
    }

    public InputStream read(ManagedFile file) throws IOException {
        return scheme(file.scheme()).read(file.path());
    }

    /** The file's size in pixels, or nothing when it is not an image the site can read. */
    public Optional<ImageSize> imageSize(ManagedFile file) {
        try (InputStream content = read(file)) {
            return imageSize(content);
        } catch (IOException unreadable) {
            return Optional.empty();
        }
    }

    private static Optional<ImageSize> imageSize(InputStream content) throws IOException {
        ImageInputStream image = ImageIO.createImageInputStream(content);
        try {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(image);
            if (!readers.hasNext()) {
                return Optional.empty();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(image);
                return Optional.of(new ImageSize(reader.getWidth(0), reader.getHeight(0)));
            } finally {
                reader.dispose();
            }
        } finally {
            image.close();
        }
    }

    /** Where a browser fetches the file: directly for a public file, through access checks for a private one. */
    public String url(ManagedFile file) {
        return scheme(file.scheme()).directUrl(file.path()).orElse(PRIVATE_URL_PREFIX + "/" + file.path());
    }

    /** Marks the file permanent, or temporary again, stamping when it changed. */
    public void setPermanent(long id, boolean permanent) {
        entities.load(FileEntityType.ID, id).ifPresent(file -> {
            Map<String, Object> fields = new LinkedHashMap<>(file.fields());
            fields.put(BaseFieldDefinition.STATUS, permanent);
            fields.put(BaseFieldDefinition.CHANGED, OffsetDateTime.now(clock));
            entities.save(file.withFields(fields));
        });
    }

    /** Removes the file's content from its scheme and forgets the file. */
    public void delete(long id) {
        find(id).ifPresent(file -> {
            try {
                scheme(file.scheme()).delete(file.path());
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
            entities.delete(FileEntityType.ID, id);
        });
    }

    /** The file a uri names, such as {@code public://2026-10/hours.pdf}. */
    public Optional<ManagedFile> findByUri(String uri) {
        return queries.query(FileEntityType.ID).condition(Condition.equal(FileEntityType.URI, uri)).ids().stream()
                .findFirst()
                .flatMap(id -> find(((Number) id).longValue()));
    }

    /** Temporary files last changed longer ago than the given age. */
    public List<ManagedFile> temporaryOlderThan(Duration age) {
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minus(age);
        List<ManagedFile> stale = new ArrayList<>();
        for (Object id : queries.query(FileEntityType.ID)
                .condition(Condition.equal(BaseFieldDefinition.STATUS, false))
                .ids()) {
            find(((Number) id).longValue())
                    .filter(file -> file.changed().isBefore(cutoff))
                    .ifPresent(stale::add);
        }
        return stale;
    }

    private FileScheme scheme(String scheme) {
        return schemes.find(scheme)
                .orElseThrow(() -> new IllegalArgumentException("No file scheme '" + scheme + "'"));
    }

    /** A name made safe to keep on disk and to serve. */
    public static String safeName(String filename) {
        String base = filename.substring(Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\')) + 1);
        String safe = base.replaceAll("[^A-Za-z0-9._-]+", "_").replaceAll("^[._]+", "");
        if (safe.isEmpty()) {
            safe = "file";
        }
        String extension = safe.contains(".") ? safe.substring(safe.lastIndexOf('.') + 1).toLowerCase() : "";
        return DANGEROUS_EXTENSIONS.contains(extension) ? safe + ".txt" : safe;
    }

    /** The name with a number before its extension, such as {@code hours_1.pdf}. */
    static String numbered(String name, int suffix) {
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) + "_" + suffix + name.substring(dot) : name + "_" + suffix;
    }
}
