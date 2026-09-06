package dev.springdrop.kernel.validation.constraints;

import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.validation.Constraint;
import dev.springdrop.kernel.validation.ValidationContext;
import java.util.Map;
import java.util.Optional;

/**
 * Requires a value to be unused by any other object, asking storage through the
 * context's lookup. The {@code field} option names the stored value to compare
 * against. A missing value passes.
 */
@SpringDropPlugin(id = UniqueFieldConstraint.ID, type = Constraint.class)
public class UniqueFieldConstraint implements Constraint {

    public static final String ID = "unique_field";

    @Override
    public Optional<String> validate(Object value, Map<String, Object> options, ValidationContext context) {
        if (value == null) {
            return Optional.empty();
        }
        String field = (String) options.get("field");
        return context.lookup().valueInUse(field, value)
                ? Optional.of("This value is already in use.")
                : Optional.empty();
    }
}
