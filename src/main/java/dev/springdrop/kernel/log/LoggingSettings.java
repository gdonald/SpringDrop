package dev.springdrop.kernel.log;

import java.util.List;

/** The logging settings, stored as the config object {@code system.logging}: the backends events are written to. */
public record LoggingSettings(List<String> backends) {

    public static final String CONFIG_NAME = "system.logging";

    public static final LoggingSettings DEFAULTS = new LoggingSettings(List.of(DatabaseLog.ID));

    public LoggingSettings {
        backends = List.copyOf(backends);
    }
}
