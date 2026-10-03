package dev.springdrop.kernel.contact;

/** The permissions contact forms are gated by, declared in {@code contact.permissions.yml}. */
public interface ContactPermissions {

    /** Sending messages through the site's contact forms. */
    String SITE_WIDE = "access site-wide contact form";

    /** Sending messages through people's personal contact forms. */
    String PERSONAL = "access user contact forms";

    /** Adding contact forms and changing where their messages go. */
    String ADMINISTER = "administer contact forms";
}
