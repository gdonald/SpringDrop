package dev.springdrop.kernel.field.formatter.types;

import java.time.Duration;
import java.time.Instant;

/**
 * How long ago a moment was, or how far off it is, in the largest unit that
 * still says something: "3 days ago", "in 2 hours", "just now".
 */
interface RelativeTime {

    String JUST_NOW = "just now";

    static String of(Instant moment, Instant now) {
        Duration apart = Duration.between(now, moment);
        long seconds = Math.abs(apart.getSeconds());
        if (seconds < 60) {
            return JUST_NOW;
        }

        String span = span(seconds);
        return apart.isNegative() ? span + " ago" : "in " + span;
    }

    private static String span(long seconds) {
        if (seconds < 3600) {
            return count(seconds / 60, "minute");
        }
        if (seconds < 86400) {
            return count(seconds / 3600, "hour");
        }
        if (seconds < 2592000) {
            return count(seconds / 86400, "day");
        }
        if (seconds < 31536000) {
            return count(seconds / 2592000, "month");
        }
        return count(seconds / 31536000, "year");
    }

    private static String count(long amount, String unit) {
        return amount + " " + unit + (amount == 1 ? "" : "s");
    }
}
