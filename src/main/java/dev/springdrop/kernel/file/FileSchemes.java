package dev.springdrop.kernel.file;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The places files are kept, by scheme. Core keeps public files in one directory,
 * served at {@code /files}, and private files in another, served only through
 * the private file controller.
 */
@Configuration
public class FileSchemes {

    public static final String PUBLIC = "public";

    public static final String PRIVATE = "private";

    public static final String PUBLIC_URL_PREFIX = "/files";

    @Bean
    LocalDiskScheme publicFiles(FileSystemProperties properties) {
        return new LocalDiskScheme(PUBLIC, Path.of(properties.publicPath()), PUBLIC_URL_PREFIX);
    }

    @Bean
    LocalDiskScheme privateFiles(FileSystemProperties properties) {
        return new LocalDiskScheme(PRIVATE, Path.of(properties.privatePath()), null);
    }

    /** Every scheme the site has, found by name. */
    @Bean
    Registry fileSchemeRegistry(List<FileScheme> schemes) {
        Map<String, FileScheme> byName = new LinkedHashMap<>();
        schemes.forEach(scheme -> byName.put(scheme.scheme(), scheme));
        return new Registry(byName);
    }

    /** The schemes by name. */
    public record Registry(Map<String, FileScheme> schemes) {

        public Registry {
            schemes = Map.copyOf(schemes);
        }

        public Optional<FileScheme> find(String scheme) {
            return Optional.ofNullable(schemes.get(scheme));
        }
    }
}
