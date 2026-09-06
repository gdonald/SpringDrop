package dev.springdrop.kernel.validation;

/**
 * One failed constraint: the path of the value that failed, and why. An empty
 * path means the constraint was attached to the object itself rather than to one
 * of its properties.
 */
public record ConstraintViolation(String propertyPath, String message) {
}
