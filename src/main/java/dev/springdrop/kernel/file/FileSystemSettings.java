package dev.springdrop.kernel.file;

/**
 * The file system settings, stored as the config object {@code system.file}:
 * the scheme a new upload is kept in when its field's storage names none,
 * public files served as they are or private files served through access
 * checks.
 */
public record FileSystemSettings(String defaultScheme) {

    public static final String CONFIG_NAME = "system.file";

    public static final FileSystemSettings DEFAULTS = new FileSystemSettings(FileSchemes.PUBLIC);
}
