package dev.springdrop.kernel.form;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A rule a value must satisfy, written once and enforced twice: the server
 * checks it on submission, and the renderer hands the same rule and message to
 * the browser as data attributes for the script to check before the form is
 * sent. The message lives here, so the two sides cannot drift apart.
 */
public record ValidationRule(String type, String parameter, String message) {

    public static final String REQUIRED = "required";

    public static final String MAX_LENGTH = "maxlength";

    public static final String PATTERN = "pattern";

    public static final String EMAIL = "email";

    public static final String RANGE = "range";

    public static final String REQUIRED_MESSAGE = "This value is required.";

    private static final Pattern EMAIL_ADDRESS = Pattern.compile("[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+");

    public static ValidationRule maxLength(int max) {
        return new ValidationRule(MAX_LENGTH, String.valueOf(max),
                "This value is too long. It must be at most " + max + " characters.");
    }

    public static ValidationRule pattern(String regex) {
        return new ValidationRule(PATTERN, regex, "This value is not in the expected format.");
    }

    public static ValidationRule email() {
        return new ValidationRule(EMAIL, "", "This value is not an email address.");
    }

    /** Bounds a number. An empty end leaves that side open. */
    public static ValidationRule range(String min, String max) {
        return new ValidationRule(RANGE, min + ":" + max, rangeMessage(min, max));
    }

    public ValidationRule withMessage(String newMessage) {
        return new ValidationRule(type, parameter, newMessage);
    }

    /** The violation message when the value fails, or nothing when it passes. */
    public Optional<String> check(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return satisfiedBy(value) ? Optional.empty() : Optional.of(message);
    }

    private boolean satisfiedBy(String value) {
        return switch (type) {
            case MAX_LENGTH -> value.length() <= Integer.parseInt(parameter);
            case PATTERN -> Pattern.compile(parameter).matcher(value).matches();
            case EMAIL -> EMAIL_ADDRESS.matcher(value).matches();
            case RANGE -> withinRange(value);
            default -> true;
        };
    }

    private boolean withinRange(String value) {
        double number = Double.parseDouble(value);
        String[] bounds = parameter.split(":", -1);
        if (!bounds[0].isEmpty() && number < Double.parseDouble(bounds[0])) {
            return false;
        }
        return bounds[1].isEmpty() || number <= Double.parseDouble(bounds[1]);
    }

    private static String rangeMessage(String min, String max) {
        if (min.isEmpty()) {
            return "This value should be " + max + " or less.";
        }
        if (max.isEmpty()) {
            return "This value should be " + min + " or more.";
        }
        return "This value should be between " + min + " and " + max + ".";
    }
}
