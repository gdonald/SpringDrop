package dev.springdrop.kernel.form;

/**
 * One choice offered by a select, radios, or checkboxes element: the value
 * submitted, and the label the person picks it by.
 */
public record SelectOption(String value, String label) {
}
