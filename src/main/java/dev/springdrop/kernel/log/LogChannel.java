package dev.springdrop.kernel.log;

import java.util.Map;

/** Logs events of one type, such as {@code action} or {@code user}, at a severity. */
public record LogChannel(String type, ActivityLog log) {

    public void error(String message, Map<String, String> variables) {
        log.log(type, Severity.ERROR, message, variables);
    }

    public void warning(String message, Map<String, String> variables) {
        log.log(type, Severity.WARNING, message, variables);
    }

    public void notice(String message, Map<String, String> variables) {
        log.log(type, Severity.NOTICE, message, variables);
    }

    public void info(String message, Map<String, String> variables) {
        log.log(type, Severity.INFO, message, variables);
    }
}
