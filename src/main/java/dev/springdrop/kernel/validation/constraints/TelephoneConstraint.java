package dev.springdrop.kernel.validation.constraints;

import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.validation.Constraint;
import dev.springdrop.kernel.validation.ValidationContext;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Requires a value to look like a telephone number: digits, with the spaces,
 * dashes, dots, parentheses, and leading plus that people write them with. The
 * shape of a number varies by country, so this checks the characters rather than
 * the pattern. A missing value passes.
 */
@SpringDropPlugin(id = TelephoneConstraint.ID, type = Constraint.class)
public class TelephoneConstraint implements Constraint {

    public static final String ID = "telephone";

    private static final Pattern NUMBER = Pattern.compile("\\+?[0-9][0-9 ().\\-]{3,}");

    @Override
    public Optional<String> validate(Object value, Map<String, Object> options, ValidationContext context) {
        if (value == null) {
            return Optional.empty();
        }
        return NUMBER.matcher(value.toString()).matches()
                ? Optional.empty()
                : Optional.of("This value is not a telephone number.");
    }
}
