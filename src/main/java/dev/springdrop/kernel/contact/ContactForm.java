package dev.springdrop.kernel.contact;

import java.util.List;

/**
 * A contact form: who its messages go to, what the sender is sent back, where
 * the sender lands afterwards, where it sorts, and whether it is the one
 * {@code /contact} shows. Stored as the config entity {@code contact_form.<id>},
 * which is also the bundle the Field API hangs its messages' fields on.
 *
 * <p>The {@code personal} form carries the messages people send one another
 * through their personal contact forms. It has no recipients of its own.
 */
public record ContactForm(
        String id,
        String label,
        List<String> recipients,
        String reply,
        String redirect,
        int weight,
        boolean selected) {

    public static final String PERSONAL = "personal";

    public ContactForm {
        recipients = List.copyOf(recipients);
    }

    public static ContactForm of(String id, String label, List<String> recipients) {
        return new ContactForm(id, label, recipients, "", "", 0, false);
    }

    public ContactForm withReply(String autoReply) {
        return new ContactForm(id, label, recipients, autoReply, redirect, weight, selected);
    }

    public ContactForm withRedirect(String path) {
        return new ContactForm(id, label, recipients, reply, path, weight, selected);
    }

    public ContactForm asSelected() {
        return new ContactForm(id, label, recipients, reply, redirect, weight, true);
    }

    public boolean personal() {
        return PERSONAL.equals(id);
    }
}
