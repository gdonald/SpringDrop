package dev.springdrop.kernel.views;

import dev.springdrop.kernel.entity.query.Condition;
import java.util.Optional;

/**
 * Narrows the results by a value the view is shown with, such as part of a
 * page's path. An argument handler is a plugin registered with
 * {@code @SpringDropPlugin(type = ArgumentHandler.class)}.
 */
public interface ArgumentHandler extends ViewPlugin {

    /** What to do without a value: {@code ignore}, {@code empty}, or {@code fixed}. */
    String DEFAULT_ACTION = "default_action";

    /** The value a {@code fixed} default action uses. */
    String DEFAULT_VALUE = "default_value";

    String IGNORE = "ignore";

    String EMPTY = "empty";

    String FIXED = "fixed";

    /** The condition for a value, or nothing when the value is not one the property can hold. */
    Optional<Condition> condition(HandlerConfig config, String value);
}
