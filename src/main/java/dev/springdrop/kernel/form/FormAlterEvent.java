package dev.springdrop.kernel.form;

import dev.springdrop.kernel.event.AlterEvent;

/**
 * Published after a form is built and before it is rendered or validated, so a
 * module can add, remove, or change elements. Listeners mutate the element tree
 * in place, the way Drupal's {@code hook_form_alter} does.
 */
public class FormAlterEvent extends AlterEvent<FormElement> {

    private final String formId;
    private final FormState state;

    public FormAlterEvent(String formId, FormElement form, FormState state) {
        super(form);
        this.formId = formId;
        this.state = state;
    }

    public String formId() {
        return formId;
    }

    public FormState state() {
        return state;
    }
}
