package dev.springdrop.kernel.views;

import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.entity.query.Sort;
import dev.springdrop.kernel.form.FormElement;
import java.util.Optional;

/**
 * Narrows the results by a property. A filter can be exposed, letting the
 * reader give its value through a form. A filter handler is a plugin registered
 * with {@code @SpringDropPlugin(type = FilterHandler.class)}.
 */
public interface FilterHandler extends ViewPlugin {

    /** Whether the reader gives the value. */
    String EXPOSED = "exposed";

    /** The name an exposed filter's value is given under. */
    String IDENTIFIER = "identifier";

    /** The value the filter compares with when it is not exposed. */
    String VALUE = "value";

    /**
     * The condition the filter adds for a value, or nothing to leave the
     * results as they are, such as for an exposed filter given no value.
     */
    Optional<Condition> condition(HandlerConfig config, String value);

    /**
     * The order the filter puts the results in for a value, ahead of the view's
     * sorts, such as a search's ranking. Most filters leave the order alone.
     */
    default Optional<Sort> sort(HandlerConfig config, String value) {
        return Optional.empty();
    }

    /** The control an exposed filter is given its value with. */
    FormElement exposedElement(HandlerConfig config, String current);

    /** The name an exposed filter's value is given under: its identifier, or its id. */
    static String identifier(HandlerConfig config) {
        String identifier = config.text(IDENTIFIER);
        return identifier.isEmpty() ? config.id() : identifier;
    }
}
