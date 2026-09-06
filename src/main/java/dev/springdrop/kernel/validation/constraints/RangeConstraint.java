package dev.springdrop.kernel.validation.constraints;

import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.validation.Constraint;
import dev.springdrop.kernel.validation.ValidationContext;
import java.util.Map;
import java.util.Optional;

/**
 * Bounds a numeric value between the optional {@code min} and {@code max}
 * options. A missing value passes.
 */
@SpringDropPlugin(id = RangeConstraint.ID, type = Constraint.class)
public class RangeConstraint implements Constraint {

    public static final String ID = "range";

    @Override
    public Optional<String> validate(Object value, Map<String, Object> options, ValidationContext context) {
        if (value == null) {
            return Optional.empty();
        }
        double number = ((Number) value).doubleValue();
        Number min = (Number) options.get("min");
        Number max = (Number) options.get("max");
        if (min != null && number < min.doubleValue()) {
            return Optional.of("This value should be " + min + " or more.");
        }
        if (max != null && number > max.doubleValue()) {
            return Optional.of("This value should be " + max + " or less.");
        }
        return Optional.empty();
    }
}
