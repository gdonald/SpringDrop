package dev.springdrop.kernel.validation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.validation.constraints.AllowedValuesConstraint;
import dev.springdrop.kernel.validation.constraints.CardinalityConstraint;
import dev.springdrop.kernel.validation.constraints.LengthConstraint;
import dev.springdrop.kernel.validation.constraints.NotNullConstraint;
import dev.springdrop.kernel.validation.constraints.RangeConstraint;
import dev.springdrop.kernel.validation.constraints.RegexConstraint;
import dev.springdrop.kernel.validation.constraints.UniqueFieldConstraint;
import dev.springdrop.kernel.validation.constraints.ValidReferenceConstraint;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ConstraintTest {

    private static final ValidationContext CONTEXT = ValidationContext.of("subject");

    @Nested
    class NotNull {

        private final NotNullConstraint constraint = new NotNullConstraint();

        private Optional<String> validate(Object value) {
            return constraint.validate(value, Map.of(), CONTEXT);
        }

        @Test
        void acceptsAValue() {
            assertThat(validate("Hello")).isEmpty();
        }

        @Test
        void rejectsAMissingValue() {
            assertThat(validate(null)).contains("This value is required.");
        }

        @Test
        void rejectsABlankString() {
            assertThat(validate("   ")).contains("This value is required.");
        }

        @Test
        void acceptsAValueThatIsNotText() {
            assertThat(validate(0)).isEmpty();
        }
    }

    @Nested
    class Length {

        private final LengthConstraint constraint = new LengthConstraint();

        private Optional<String> validate(Object value, Map<String, Object> options) {
            return constraint.validate(value, options, CONTEXT);
        }

        @Test
        void acceptsAValueWithinBounds() {
            assertThat(validate("Hello", Map.of("min", 2, "max", 10))).isEmpty();
        }

        @Test
        void rejectsAValueUnderTheMinimum() {
            assertThat(validate("H", Map.of("min", 2))).contains(
                    "This value is too short. It must be at least 2 characters.");
        }

        @Test
        void rejectsAValueOverTheMaximum() {
            assertThat(validate("Hello there", Map.of("max", 5))).contains(
                    "This value is too long. It must be at most 5 characters.");
        }

        @Test
        void acceptsAValueWhenNoBoundsAreGiven() {
            assertThat(validate("Hello", Map.of())).isEmpty();
        }

        @Test
        void acceptsAMissingValue() {
            assertThat(validate(null, Map.of("max", 5))).isEmpty();
        }
    }

    @Nested
    class Range {

        private final RangeConstraint constraint = new RangeConstraint();

        private Optional<String> validate(Object value, Map<String, Object> options) {
            return constraint.validate(value, options, CONTEXT);
        }

        @Test
        void acceptsANumberWithinBounds() {
            assertThat(validate(5, Map.of("min", 1, "max", 10))).isEmpty();
        }

        @Test
        void rejectsANumberUnderTheMinimum() {
            assertThat(validate(0, Map.of("min", 1))).contains("This value should be 1 or more.");
        }

        @Test
        void rejectsANumberOverTheMaximum() {
            assertThat(validate(11, Map.of("max", 10))).contains("This value should be 10 or less.");
        }

        @Test
        void acceptsANumberWhenNoBoundsAreGiven() {
            assertThat(validate(5, Map.of())).isEmpty();
        }

        @Test
        void acceptsAMissingValue() {
            assertThat(validate(null, Map.of("max", 10))).isEmpty();
        }
    }

    @Nested
    class Regex {

        private final RegexConstraint constraint = new RegexConstraint();

        private Optional<String> validate(Object value) {
            return constraint.validate(value, Map.of("pattern", "[a-z_]+"), CONTEXT);
        }

        @Test
        void acceptsAMatchingValue() {
            assertThat(validate("machine_name")).isEmpty();
        }

        @Test
        void rejectsAValueThatDoesNotMatch() {
            assertThat(validate("Machine Name")).contains("This value is not in the expected format.");
        }

        @Test
        void acceptsAMissingValue() {
            assertThat(validate(null)).isEmpty();
        }
    }

    @Nested
    class AllowedValues {

        private final AllowedValuesConstraint constraint = new AllowedValuesConstraint();

        private Optional<String> validate(Object value) {
            return constraint.validate(value, Map.of("values", List.of("draft", "published")), CONTEXT);
        }

        @Test
        void acceptsAnAllowedValue() {
            assertThat(validate("draft")).isEmpty();
        }

        @Test
        void rejectsAValueOutsideTheList() {
            assertThat(validate("archived")).contains("This value is not one of the allowed values.");
        }

        @Test
        void acceptsAMissingValue() {
            assertThat(validate(null)).isEmpty();
        }
    }

    @Nested
    class Cardinality {

        private final CardinalityConstraint constraint = new CardinalityConstraint();

        private Optional<String> validate(Object value) {
            return constraint.validate(value, Map.of("max", 2), CONTEXT);
        }

        @Test
        void acceptsAsManyValuesAsAllowed() {
            assertThat(validate(List.of("one", "two"))).isEmpty();
        }

        @Test
        void acceptsASingleValue() {
            assertThat(validate("one")).isEmpty();
        }

        @Test
        void rejectsMoreValuesThanAllowed() {
            assertThat(validate(List.of("one", "two", "three")))
                    .contains("This field holds at most 2 value(s), and 3 were given.");
        }

        @Test
        void acceptsAMissingValue() {
            assertThat(validate(null)).isEmpty();
        }
    }

    @Nested
    class UniqueField {

        private final UniqueFieldConstraint constraint = new UniqueFieldConstraint();

        private Optional<String> validate(Object value, ValueLookup lookup) {
            return constraint.validate(value, Map.of("field", "mail"), new ValidationContext("subject", lookup));
        }

        @Test
        void acceptsAValueNoOneElseHolds() {
            assertThat(validate("new@example.com", ValueLookup.permissive())).isEmpty();
        }

        @Test
        void rejectsAValueAlreadyInUse() {
            assertThat(validate("taken@example.com", inUse())).contains("This value is already in use.");
        }

        @Test
        void acceptsAMissingValue() {
            assertThat(validate(null, inUse())).isEmpty();
        }

        private ValueLookup inUse() {
            return new ValueLookup() {
                @Override
                public boolean valueInUse(String propertyPath, Object value) {
                    return true;
                }

                @Override
                public boolean referenceExists(String target, Object id) {
                    return true;
                }
            };
        }
    }

    @Nested
    class ValidReference {

        private final ValidReferenceConstraint constraint = new ValidReferenceConstraint();

        private Optional<String> validate(Object value, ValueLookup lookup) {
            return constraint.validate(value, Map.of("target", "user"), new ValidationContext("subject", lookup));
        }

        @Test
        void acceptsAReferenceThatExists() {
            assertThat(validate(42, ValueLookup.permissive())).isEmpty();
        }

        @Test
        void rejectsAReferenceThatDoesNotExist() {
            assertThat(validate(42, missing())).contains("This reference does not exist.");
        }

        @Test
        void acceptsAMissingValue() {
            assertThat(validate(null, missing())).isEmpty();
        }

        private ValueLookup missing() {
            return new ValueLookup() {
                @Override
                public boolean valueInUse(String propertyPath, Object value) {
                    return false;
                }

                @Override
                public boolean referenceExists(String target, Object id) {
                    return false;
                }
            };
        }
    }

    @Nested
    class PermissiveLookup {

        @Test
        void reportsNoValueAsTaken() {
            assertThat(ValueLookup.permissive().valueInUse("mail", "someone@example.com")).isFalse();
        }

        @Test
        void reportsEveryReferenceAsPresent() {
            assertThat(ValueLookup.permissive().referenceExists("user", 42)).isTrue();
        }
    }
}
