package dev.springdrop.kernel.validation.constraints;

import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.validation.Constraint;
import dev.springdrop.kernel.validation.ValidationContext;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Requires a range to hold a start and an end that both parse, with the end no
 * earlier than the start. The {@code datetime_type} option says whether the two
 * ends are dates or dates and times. A missing value passes.
 */
@SpringDropPlugin(id = DateRangeConstraint.ID, type = Constraint.class)
public class DateRangeConstraint implements Constraint {

    public static final String ID = "date_range";

    public static final String START = "start";

    public static final String END = "end";

    @Override
    public Optional<String> validate(Object value, Map<String, Object> options, ValidationContext context) {
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof Map<?, ?> range)) {
            return Optional.of("This value is not a range.");
        }

        Object start = range.get(START);
        Object end = range.get(END);
        if (start == null || end == null) {
            return Optional.of("A range needs both a start and an end.");
        }

        boolean dateOnly = DateTimeConstraint.DATE_ONLY.equals(options.get(DateTimeConstraint.TYPE_OPTION));
        if (!DateTimeConstraint.parses(start.toString(), dateOnly)
                || !DateTimeConstraint.parses(end.toString(), dateOnly)) {
            return Optional.of("This range is not written as dates.");
        }

        return (compare(start.toString(), end.toString(), dateOnly) > 0)
                ? Optional.of("The end of this range comes before its start.")
                : Optional.empty();
    }

    private static int compare(String start, String end, boolean dateOnly) {
        if (dateOnly) {
            return LocalDate.parse(start).compareTo(LocalDate.parse(end));
        }
        return OffsetDateTime.parse(start).toInstant().compareTo(OffsetDateTime.parse(end).toInstant());
    }
}
