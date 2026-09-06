package dev.springdrop.kernel.user;

/**
 * What becomes of an account and what it wrote when it is closed.
 */
public enum CancellationMethod {

    /** The account is closed; everything it wrote stays as it is. */
    BLOCK("Block the account and keep its content"),

    /** The account is closed and everything it wrote is taken out of sight. */
    BLOCK_AND_UNPUBLISH("Block the account and unpublish its content"),

    /** The account goes; what it wrote stays, credited to nobody. */
    REASSIGN_TO_ANONYMOUS("Delete the account and keep its content, credited to Anonymous"),

    /** The account goes and takes everything it wrote with it. */
    DELETE("Delete the account and its content");

    private final String label;

    CancellationMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
