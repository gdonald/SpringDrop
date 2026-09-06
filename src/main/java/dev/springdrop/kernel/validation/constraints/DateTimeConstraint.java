package dev.springdrop.kernel.validation.constraints;

import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.validation.Constraint;
import dev.springdrop.kernel.validation.ValidationContext;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

/**
 * Requires a value to be a date, or a date and time, written in ISO-8601. The
 * {@code datetime_type} option says which of the two a field stores. A missing
 * value passes.
 */
@SpringDropPlugin(id = DateTimeConstraint.ID, type = Constraint.class)
public class DateTimeConstraint implements Constraint {

    public static final String ID = "datetime";

    public static final String TYPE_OPTION = "datetime_type";

    public static final String DATE_ONLY = "date";

    public static final String DATE_AND_TIME = "datetime";

    @Override
    public Optional<String> validate(Object value, Map<String, Object> options, ValidationContext context) {
        if (value == null) {
            return Optional.empty();
        }
        boolean dateOnly = DATE_ONLY.equals(options.get(TYPE_OPTION));
        return parses(value.toString(), dateOnly)
                ? Optional.empty()
                : Optional.of(dateOnly
                        ? "This value is not a date."
                        : "This value is not a date and time.");
    }

    static boolean parses(String value, boolean dateOnly) {
        try {
            if (dateOnly) {
                LocalDate.parse(value);
            } else {
                OffsetDateTime.parse(value);
            }
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
