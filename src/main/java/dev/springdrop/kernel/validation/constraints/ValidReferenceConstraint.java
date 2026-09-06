package dev.springdrop.kernel.validation.constraints;

import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.validation.Constraint;
import dev.springdrop.kernel.validation.ValidationContext;
import java.util.Map;
import java.util.Optional;

/**
 * Requires a reference to point at something that exists, asking storage through
 * the context's lookup. The {@code target} option names what is referenced. A
 * missing value passes, so an optional reference is left to {@code not_null}.
 */
@SpringDropPlugin(id = ValidReferenceConstraint.ID, type = Constraint.class)
public class ValidReferenceConstraint implements Constraint {

    public static final String ID = "valid_reference";

    @Override
    public Optional<String> validate(Object value, Map<String, Object> options, ValidationContext context) {
        if (value == null) {
            return Optional.empty();
        }
        String target = (String) options.get("target");
        return context.lookup().referenceExists(target, value)
                ? Optional.empty()
                : Optional.of("This reference does not exist.");
    }
}
