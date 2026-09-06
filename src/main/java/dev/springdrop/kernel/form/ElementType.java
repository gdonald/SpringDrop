package dev.springdrop.kernel.form;

/**
 * The kinds of element a form is built from. Each maps to one Bootstrap control,
 * wrapper, or piece of text when the form is rendered.
 */
public enum ElementType {

    CONTAINER,
    FIELDSET,
    DETAILS,
    VERTICAL_TABS,
    TEXTFIELD,
    PASSWORD,
    TEXTAREA,
    SELECT,
    RADIOS,
    CHECKBOXES,
    CHECKBOX,
    NUMBER,
    DATE,
    TIME,
    DATETIME,
    FILE,
    HIDDEN,
    VALUE,
    TEXT,
    LINK,
    SUBMIT,
    ACTIONS
}
