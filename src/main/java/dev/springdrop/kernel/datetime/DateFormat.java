package dev.springdrop.kernel.datetime;

/**
 * A named date format: a human label and a {@link java.time.format.DateTimeFormatter}
 * pattern. Stored as a config object so it can be customized per site.
 */
public record DateFormat(String label, String pattern) {
}
