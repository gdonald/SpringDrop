package dev.springdrop.kernel.form;

/**
 * The shape every destructive action takes: a question, what will happen, a
 * single confirm button, and a way back. Nothing happens until the confirming
 * POST arrives, so a link can never carry out the action on its own.
 */
public abstract class ConfirmForm implements Form {

    public static final String CONFIRM = "confirm";

    public static final String CANCEL = "cancel";

    /** The question put to the person, such as "Delete the article Hello?". */
    protected abstract String question();

    /** Where the cancel link goes back to. */
    protected abstract String cancelPath();

    /** What is carried out once the person confirms. */
    protected abstract void confirmed(FormState state);

    /** What the action will do, shown under the question. */
    protected String description() {
        return "This action cannot be undone.";
    }

    protected String confirmLabel() {
        return "Confirm";
    }

    @Override
    public FormElement build(FormState state) {
        return FormElement.of(ElementType.CONTAINER, id())
                .child(FormElement.of(ElementType.TEXT, "question")
                        .label(question())
                        .attribute("class", "h5"))
                .child(FormElement.of(ElementType.TEXT, "description")
                        .label(description())
                        .attribute("class", "text-body-secondary"))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, CONFIRM).label(confirmLabel()))
                        .child(FormElement.of(ElementType.LINK, CANCEL)
                                .label("Cancel")
                                .value(cancelPath())));
    }

    @Override
    public final void submit(FormState state) {
        confirmed(state);
    }
}
