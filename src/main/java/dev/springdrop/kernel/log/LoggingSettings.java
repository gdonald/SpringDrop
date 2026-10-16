package dev.springdrop.kernel.log;

import java.util.List;

/**
 * The logging settings, stored as the config object {@code system.logging}:
 * the backends events are written to, how much an error page tells its reader,
 * and how many events the database log keeps, 0 for every one.
 */
public record LoggingSettings(List<String> backends, ErrorDisplay errorDisplay, Integer databaseLogRows) {

    public static final String CONFIG_NAME = "system.logging";

    public static final int DEFAULT_ROWS = 1000;

    public static final LoggingSettings DEFAULTS = new LoggingSettings(List.of(DatabaseLog.ID), ErrorDisplay.NONE,
            DEFAULT_ROWS);

    public LoggingSettings {
        backends = backends == null ? List.of(DatabaseLog.ID) : List.copyOf(backends);
        errorDisplay = errorDisplay == null ? ErrorDisplay.NONE : errorDisplay;
        databaseLogRows = databaseLogRows == null ? 0 : databaseLogRows;
    }

    public LoggingSettings(List<String> backends) {
        this(backends, ErrorDisplay.NONE, DEFAULT_ROWS);
    }
}
