package dev.springdrop.kernel.validation.constraints;

import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.validation.Constraint;
import dev.springdrop.kernel.validation.ValidationContext;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Requires a value to be an email address: a local part, an at sign, and a
 * domain with a dot in it. A missing value passes.
 */
@SpringDropPlugin(id = EmailConstraint.ID, type = Constraint.class)
public class EmailConstraint implements Constraint {

    public static final String ID = "email";

    private static final Pattern ADDRESS = Pattern.compile("[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+");

    @Override
    public Optional<String> validate(Object value, Map<String, Object> options, ValidationContext context) {
        if (value == null) {
            return Optional.empty();
        }
        return ADDRESS.matcher(value.toString()).matches()
                ? Optional.empty()
                : Optional.of("This value is not an email address.");
    }
}
