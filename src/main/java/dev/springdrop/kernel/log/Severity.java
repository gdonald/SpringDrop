package dev.springdrop.kernel.log;

import java.util.Arrays;
import java.util.Optional;

/** How serious a logged event is, with the RFC 5424 syslog levels, emergency the most serious. */
public enum Severity {
    EMERGENCY(0, "Emergency"),
    ALERT(1, "Alert"),
    CRITICAL(2, "Critical"),
    ERROR(3, "Error"),
    WARNING(4, "Warning"),
    NOTICE(5, "Notice"),
    INFO(6, "Info"),
    DEBUG(7, "Debug");

    private final int level;
    private final String label;

    Severity(int level, String label) {
        this.level = level;
        this.label = label;
    }

    public int level() {
        return level;
    }

    public String label() {
        return label;
    }

    public static Optional<Severity> ofLevel(int level) {
        return Arrays.stream(values()).filter(severity -> severity.level == level).findFirst();
    }
}
