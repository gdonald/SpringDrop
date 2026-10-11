package dev.springdrop.kernel.language;

import java.util.List;

/**
 * How a request's language is chosen, stored as the config object
 * {@code language.negotiation}: the methods tried, in order, and whether the
 * URL method reads a path prefix or the domain. When no method chooses a
 * language, the request is in the site's default language.
 */
public record NegotiationSettings(List<String> methods, String urlSource) {

    public static final String CONFIG_NAME = "language.negotiation";

    /** The language a URL's prefix or domain names. */
    public static final String URL = "url";

    /** The language an account prefers. */
    public static final String USER = "user";

    /** The language a {@code ?language=} parameter chose, kept in the session. */
    public static final String SESSION = "session";

    public static final List<String> ALL_METHODS = List.of(URL, USER, SESSION);

    public static final String PREFIX = "prefix";

    public static final String DOMAIN = "domain";

    public static final NegotiationSettings DEFAULTS = new NegotiationSettings(List.of(URL, USER), PREFIX);

    public NegotiationSettings {
        methods = List.copyOf(methods);
    }
}
