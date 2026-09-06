package dev.springdrop.kernel.entity;

import dev.springdrop.kernel.validation.ConstraintViolation;
import java.util.List;

/**
 * Raised when an entity fails validation, aborting the save before anything is
 * written. It carries every violation so a form can show them all at once.
 */
public class EntityValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<ConstraintViolation> violations;

    public EntityValidationException(String entityType, List<ConstraintViolation> violations) {
        super("Validation failed for entity of type '" + entityType + "'");
        this.violations = List.copyOf(violations);
    }

    public List<ConstraintViolation> violations() {
        return violations;
    }
}
