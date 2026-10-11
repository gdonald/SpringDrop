package dev.springdrop.kernel.log;

/** Somewhere logged events are written. The logging settings name the backends in use by id. */
public interface LogBackend {

    String id();

    void write(LogEntry entry);
}
