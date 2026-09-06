package dev.springdrop.kernel.validation.constraints;

import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.validation.Constraint;
import dev.springdrop.kernel.validation.ValidationContext;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Requires a text value to match the {@code pattern} option in full. A missing
 * value passes.
 */
@SpringDropPlugin(id = RegexConstraint.ID, type = Constraint.class)
public class RegexConstraint implements Constraint {

    public static final String ID = "regex";

    @Override
    public Optional<String> validate(Object value, Map<String, Object> options, ValidationContext context) {
        if (value == null) {
            return Optional.empty();
        }
        Pattern pattern = Pattern.compile((String) options.get("pattern"));
        return pattern.matcher(value.toString()).matches()
                ? Optional.empty()
                : Optional.of("This value is not in the expected format.");
    }
}
