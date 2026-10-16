package dev.springdrop.kernel.config.sync;

/** A file in the sync directory that cannot be read as a config object. */
public class ConfigFileException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConfigFileException(String message) {
        super(message);
    }
}
