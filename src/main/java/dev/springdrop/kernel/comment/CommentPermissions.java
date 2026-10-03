package dev.springdrop.kernel.comment;

/** The permissions comments are gated by, declared in {@code comment.permissions.yml}. */
public interface CommentPermissions {

    /** Reading published comments. */
    String ACCESS_COMMENTS = "access comments";

    /** Writing comments where they are open. */
    String POST_COMMENTS = "post comments";

    /** Having one's comments published without waiting for approval. */
    String SKIP_APPROVAL = "skip comment approval";

    /** Changing one's own comments. */
    String EDIT_OWN = "edit own comments";

    /** Approving, changing, and deleting every comment. */
    String ADMINISTER_COMMENTS = "administer comments";
}
