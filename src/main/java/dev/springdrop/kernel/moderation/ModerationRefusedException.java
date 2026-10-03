package dev.springdrop.kernel.moderation;

/** A move between moderation states the workflow does not allow, or the person may not make. */
public class ModerationRefusedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ModerationRefusedException(String reason) {
        super(reason);
    }
}
