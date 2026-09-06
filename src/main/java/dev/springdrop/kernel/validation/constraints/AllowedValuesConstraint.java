package dev.springdrop.kernel.validation.constraints;

import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.validation.Constraint;
import dev.springdrop.kernel.validation.ValidationContext;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Restricts a value to the {@code values} option, the list a select field offers.
 * A missing value passes.
 */
@SpringDropPlugin(id = AllowedValuesConstraint.ID, type = Constraint.class)
public class AllowedValuesConstraint implements Constraint {

    public static final String ID = "allowed_values";

    @Override
    public Optional<String> validate(Object value, Map<String, Object> options, ValidationContext context) {
        if (value == null) {
            return Optional.empty();
        }
        Collection<?> allowed = (Collection<?>) options.get("values");
        return allowed.contains(value)
                ? Optional.empty()
                : Optional.of("This value is not one of the allowed values.");
    }
}
