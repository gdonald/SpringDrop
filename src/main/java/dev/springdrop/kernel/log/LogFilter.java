package dev.springdrop.kernel.log;

import java.util.Optional;

/** Which logged events a listing shows: of one type, at one severity, or both. Empty shows every one. */
public record LogFilter(Optional<String> type, Optional<Severity> severity) {

    public static final LogFilter ALL = new LogFilter(Optional.empty(), Optional.empty());
}
