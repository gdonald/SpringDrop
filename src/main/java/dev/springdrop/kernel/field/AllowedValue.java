package dev.springdrop.kernel.field;

/**
 * One option a list field offers: the value that is stored, and the label an
 * editor picks it by. Labels can be reworded without touching stored values.
 */
public record AllowedValue(Object value, String label) {
}
