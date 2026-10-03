package dev.springdrop.kernel.taxonomy;

/** A parent that would break a vocabulary's tree: another vocabulary's term, the term itself, or one below it. */
public class TermHierarchyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TermHierarchyException(String reason) {
        super(reason);
    }
}
