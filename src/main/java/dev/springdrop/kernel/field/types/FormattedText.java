package dev.springdrop.kernel.field.types;

import java.util.Map;
import java.util.Optional;

/**
 * The parts one value of a formatted text field holds: the text, the format it
 * is written in, and for text with a summary, the summary.
 */
public interface FormattedText {

    String VALUE = "value";

    String FORMAT = "format";

    String SUMMARY = "summary";

    /** The storage setting bounding a single-line formatted text's length. */
    String MAX_LENGTH = "max_length";

    /** The instance setting showing the summary input on the form. */
    String DISPLAY_SUMMARY = "display_summary";

    /** The instance setting requiring a summary. */
    String REQUIRED_SUMMARY = "required_summary";

    static String part(Object value, String key) {
        return Optional.ofNullable(value)
                .filter(Map.class::isInstance)
                .map(stored -> ((Map<?, ?>) stored).get(key))
                .map(String::valueOf)
                .orElse("");
    }
}
