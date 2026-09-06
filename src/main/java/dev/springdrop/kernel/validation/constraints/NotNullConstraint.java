package dev.springdrop.kernel.validation.constraints;

import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.validation.Constraint;
import dev.springdrop.kernel.validation.ValidationContext;
import java.util.Map;
import java.util.Optional;

/**
 * Requires a value to be present. A blank string counts as absent, so a required
 * text field cannot be satisfied by whitespace.
 */
@SpringDropPlugin(id = NotNullConstraint.ID, type = Constraint.class)
public class NotNullConstraint implements Constraint {

    public static final String ID = "not_null";

    @Override
    public Optional<String> validate(Object value, Map<String, Object> options, ValidationContext context) {
        boolean absent = (value == null) || (value instanceof String text && text.isBlank());
        return absent ? Optional.of("This value is required.") : Optional.empty();
    }
}
