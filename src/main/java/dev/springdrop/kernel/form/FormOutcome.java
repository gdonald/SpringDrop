package dev.springdrop.kernel.form;

/**
 * What came of handling a submission: whether the form was submitted, the markup
 * to send back when it was not, and the state as it stood at the end. A form
 * that submitted has nothing to render, since the controller redirects instead.
 */
public record FormOutcome(boolean submitted, String markup, FormState state) {
}
