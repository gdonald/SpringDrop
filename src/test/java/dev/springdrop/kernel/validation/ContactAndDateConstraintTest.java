package dev.springdrop.kernel.validation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.validation.constraints.DateRangeConstraint;
import dev.springdrop.kernel.validation.constraints.DateTimeConstraint;
import dev.springdrop.kernel.validation.constraints.EmailConstraint;
import dev.springdrop.kernel.validation.constraints.LinkConstraint;
import dev.springdrop.kernel.validation.constraints.TelephoneConstraint;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ContactAndDateConstraintTest {

    private static final ValidationContext CONTEXT = ValidationContext.of("subject");

    @Nested
    class DateTime {

        private final DateTimeConstraint constraint = new DateTimeConstraint();

        private Optional<String> validate(Object value, String datetimeType) {
            return constraint.validate(value, Map.of(DateTimeConstraint.TYPE_OPTION, datetimeType), CONTEXT);
        }

        @Test
        void acceptsAMissingValue() {
            assertThat(validate(null, DateTimeConstraint.DATE_AND_TIME)).isEmpty();
        }

        @Test
        void rejectsTextThatIsNotADateAndTime() {
            assertThat(validate("soon", DateTimeConstraint.DATE_AND_TIME))
                    .contains("This value is not a date and time.");
        }

        @Test
        void rejectsTextThatIsNotADate() {
            assertThat(validate("soon", DateTimeConstraint.DATE_ONLY)).contains("This value is not a date.");
        }
    }

    @Nested
    class DateRange {

        private final DateRangeConstraint constraint = new DateRangeConstraint();

        private Optional<String> validate(Object value) {
            return constraint.validate(value, Map.of(), CONTEXT);
        }

        @Test
        void acceptsAMissingValue() {
            assertThat(validate(null)).isEmpty();
        }

        @Test
        void rejectsARangeWithoutAStart() {
            assertThat(validate(Map.of(DateRangeConstraint.END, "2026-03-05T19:00:00Z")))
                    .contains("A range needs both a start and an end.");
        }

        @Test
        void rejectsARangeWhoseEndIsNotADate() {
            assertThat(validate(Map.of(
                    DateRangeConstraint.START, "2026-03-05T19:00:00Z",
                    DateRangeConstraint.END, "later")))
                    .contains("This range is not written as dates.");
        }

        @Test
        void acceptsARangeThatStartsAndEndsAtTheSameMoment() {
            assertThat(validate(Map.of(
                    DateRangeConstraint.START, "2026-03-05T19:00:00Z",
                    DateRangeConstraint.END, "2026-03-05T19:00:00Z"))).isEmpty();
        }
    }

    @Nested
    class Email {

        private final EmailConstraint constraint = new EmailConstraint();

        @Test
        void acceptsAMissingValue() {
            assertThat(constraint.validate(null, Map.of(), CONTEXT)).isEmpty();
        }

        @Test
        void acceptsAnAddress() {
            assertThat(constraint.validate("alice@example.com", Map.of(), CONTEXT)).isEmpty();
        }

        @Test
        void rejectsAnAddressWithoutADomainDot() {
            assertThat(constraint.validate("alice@example", Map.of(), CONTEXT))
                    .contains("This value is not an email address.");
        }
    }

    @Nested
    class Telephone {

        private final TelephoneConstraint constraint = new TelephoneConstraint();

        @Test
        void acceptsAMissingValue() {
            assertThat(constraint.validate(null, Map.of(), CONTEXT)).isEmpty();
        }

        @Test
        void acceptsANumberWithSeparators() {
            assertThat(constraint.validate("+1 555-0100", Map.of(), CONTEXT)).isEmpty();
        }

        @Test
        void rejectsWordsInsteadOfDigits() {
            assertThat(constraint.validate("ring me", Map.of(), CONTEXT))
                    .contains("This value is not a telephone number.");
        }
    }

    @Nested
    class Link {

        private final LinkConstraint constraint = new LinkConstraint();

        private Optional<String> validate(Object value) {
            return constraint.validate(value, Map.of(), CONTEXT);
        }

        @Test
        void acceptsAMissingValue() {
            assertThat(validate(null)).isEmpty();
        }

        @Test
        void acceptsAnInternalPath() {
            assertThat(validate(Map.of(LinkConstraint.URI_KEY, "internal:/about"))).isEmpty();
        }

        @Test
        void rejectsAValueThatIsNotALink() {
            assertThat(validate("https://example.com")).contains("This value is not a link.");
        }

        @Test
        void rejectsALinkWithoutAUri() {
            assertThat(validate(Map.of(LinkConstraint.TITLE_KEY, "Nowhere")))
                    .contains("A link needs a uri.");
        }

        @Test
        void readsThePathOfAnInternalLink() {
            assertThat(LinkConstraint.internalPath("internal:/about")).isEqualTo("/about");
        }
    }
}
