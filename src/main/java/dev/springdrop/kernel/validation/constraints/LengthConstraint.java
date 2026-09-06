package dev.springdrop.kernel.validation.constraints;

import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.validation.Constraint;
import dev.springdrop.kernel.validation.ValidationContext;
import java.util.Map;
import java.util.Optional;

/**
 * Bounds the length of a text value. The {@code min} and {@code max} options are
 * each optional; a missing value passes, so pair this with {@code not_null} when
 * the value is required.
 */
@SpringDropPlugin(id = LengthConstraint.ID, type = Constraint.class)
public class LengthConstraint implements Constraint {

    public static final String ID = "length";

    @Override
    public Optional<String> validate(Object value, Map<String, Object> options, ValidationContext context) {
        if (value == null) {
            return Optional.empty();
        }
        int length = value.toString().length();
        Number min = (Number) options.get("min");
        Number max = (Number) options.get("max");
        if (min != null && length < min.intValue()) {
            return Optional.of("This value is too short. It must be at least " + min.intValue() + " characters.");
        }
        if (max != null && length > max.intValue()) {
            return Optional.of("This value is too long. It must be at most " + max.intValue() + " characters.");
        }
        return Optional.empty();
    }
}
