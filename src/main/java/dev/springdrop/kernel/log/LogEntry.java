package dev.springdrop.kernel.log;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One logged event: its type (the channel it was logged on), severity, the
 * message with {@code {name}} placeholders, the values for them, the account
 * that caused it, the address requested and the page it came from, the client
 * address, and when it happened. The id is null until the database stores it.
 */
public record LogEntry(
        Long id,
        String type,
        Severity severity,
        String message,
        Map<String, String> variables,
        long uid,
        String location,
        String referer,
        String hostname,
        Instant timestamp) {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_]+)}");

    public LogEntry {
        variables = Map.copyOf(variables);
    }

    /** The message with each placeholder replaced by its variable. A placeholder with no variable is left. */
    public String formatted() {
        Matcher matcher = PLACEHOLDER.matcher(message);
        StringBuilder text = new StringBuilder();
        while (matcher.find()) {
            String value = variables.getOrDefault(matcher.group(1), matcher.group());
            matcher.appendReplacement(text, Matcher.quoteReplacement(value));
        }
        return matcher.appendTail(text).toString();
    }
}
