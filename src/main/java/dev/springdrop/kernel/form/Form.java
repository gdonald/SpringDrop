package dev.springdrop.kernel.form;

/**
 * One form: how it is built, what it checks, and what it does when the values
 * pass. A form is a bean, so it can depend on whatever services it needs, and it
 * is reached by its id.
 */
public interface Form {

    String id();

    /** Builds the element tree, given the state of this submission. */
    FormElement build(FormState state);

    /** Records an error for every value that will not do. */
    default void validate(FormState state) {
    }

    /** Acts on values that passed. */
    void submit(FormState state);
}
