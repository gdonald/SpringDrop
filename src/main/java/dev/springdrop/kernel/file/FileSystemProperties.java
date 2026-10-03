package dev.springdrop.kernel.file;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where files are kept on disk: public files, served as they are, and private
 * files, served only through access checks. Relative paths are read from the
 * directory the application runs in.
 */
@ConfigurationProperties("springdrop.files")
public record FileSystemProperties(
        @DefaultValue("files/public") String publicPath,
        @DefaultValue("files/private") String privatePath) {
}
