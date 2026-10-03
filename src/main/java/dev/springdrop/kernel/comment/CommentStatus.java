package dev.springdrop.kernel.comment;

/** Whether an entity takes comments, as its comment field holds it. */
public enum CommentStatus {

    /** Comments are neither shown nor taken. */
    HIDDEN(0, "Hidden"),

    /** Comments are shown, and no more are taken. */
    CLOSED(1, "Closed"),

    /** Comments are shown and taken. */
    OPEN(2, "Open");

    private final int value;
    private final String label;

    CommentStatus(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int value() {
        return value;
    }

    public String label() {
        return label;
    }

    /** The status a stored value names, which for anything else is hidden. */
    public static CommentStatus of(Object stored) {
        for (CommentStatus status : values()) {
            if (String.valueOf(status.value).equals(String.valueOf(stored))) {
                return status;
            }
        }
        return HIDDEN;
    }
}
