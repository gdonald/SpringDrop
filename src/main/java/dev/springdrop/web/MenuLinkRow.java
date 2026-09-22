package dev.springdrop.web;

/**
 * One line of the link admin: the link as it is drawn in the table, how deep it
 * sits, and whether it is one the site may edit. A link a module declared in
 * code is shown but cannot be edited or deleted here, since it is defined in the
 * module rather than stored.
 */
public record MenuLinkRow(
        String id,
        String title,
        String url,
        int depth,
        int weight,
        boolean enabled,
        boolean stored) {

    /** The indent the table draws for a link this deep in the menu. */
    public String indent() {
        return "  ".repeat(depth);
    }
}
