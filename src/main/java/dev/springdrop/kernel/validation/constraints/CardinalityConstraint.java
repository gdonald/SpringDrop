package dev.springdrop.kernel.validation.constraints;

import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.validation.Constraint;
import dev.springdrop.kernel.validation.ValidationContext;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Limits how many values a field holds. The {@code max} option is the field's
 * cardinality; a single value counts as one. A missing value passes, so a field
 * that must be filled in is left to {@code not_null}.
 */
@SpringDropPlugin(id = CardinalityConstraint.ID, type = Constraint.class)
public class CardinalityConstraint implements Constraint {

    public static final String ID = "cardinality";

    @Override
    public Optional<String> validate(Object value, Map<String, Object> options, ValidationContext context) {
        if (value == null) {
            return Optional.empty();
        }
        int count = (value instanceof Collection<?> values) ? values.size() : 1;
        int max = ((Number) options.get("max")).intValue();
        return (count > max)
                ? Optional.of("This field holds at most " + max + " value(s), and " + count + " were given.")
                : Optional.empty();
    }
}
