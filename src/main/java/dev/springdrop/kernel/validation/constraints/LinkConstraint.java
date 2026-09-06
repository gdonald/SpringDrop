package dev.springdrop.kernel.validation.constraints;

import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.validation.Constraint;
import dev.springdrop.kernel.validation.ValidationContext;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Optional;

/**
 * Requires a link to carry a uri that parses. An internal link is written
 * {@code internal:/path}; anything else is an absolute uri with a scheme. The
 * title beside it is optional. A missing value passes.
 */
@SpringDropPlugin(id = LinkConstraint.ID, type = Constraint.class)
public class LinkConstraint implements Constraint {

    public static final String ID = "link";

    public static final String URI_KEY = "uri";

    public static final String TITLE_KEY = "title";

    public static final String INTERNAL_SCHEME = "internal:";

    @Override
    public Optional<String> validate(Object value, Map<String, Object> options, ValidationContext context) {
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof Map<?, ?> link)) {
            return Optional.of("This value is not a link.");
        }

        Object uri = link.get(URI_KEY);
        if (uri == null) {
            return Optional.of("A link needs a uri.");
        }
        return isUsable(uri.toString())
                ? Optional.empty()
                : Optional.of("This value is not a valid uri.");
    }

    static boolean isUsable(String uri) {
        if (uri.startsWith(INTERNAL_SCHEME)) {
            return internalPath(uri).startsWith("/");
        }
        try {
            return new URI(uri).isAbsolute();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /** The site path an internal link points at. */
    public static String internalPath(String uri) {
        return uri.substring(INTERNAL_SCHEME.length());
    }
}
