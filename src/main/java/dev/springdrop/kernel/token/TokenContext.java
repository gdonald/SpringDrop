package dev.springdrop.kernel.token;

import java.util.Map;

/**
 * Inputs for token replacement: the named objects available to providers, and
 * two modes. {@code sanitize} HTML-escapes replaced values for HTML output;
 * {@code clearUnknown} drops unresolved tokens instead of leaving them in place.
 */
public record TokenContext(Map<String, Object> data, boolean sanitize, boolean clearUnknown) {

    public static TokenContext of(Map<String, Object> data) {
        return new TokenContext(data, false, false);
    }
}
