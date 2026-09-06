package dev.springdrop.kernel.validation;

import java.util.Map;

/**
 * A constraint attached to a value: which constraint, where it applies, the
 * options it runs with, and whether it checks the value as a whole or each item
 * of it. The property path is dot-separated and walks the object graph; an empty
 * path attaches the constraint to the object itself. A per-item constraint
 * applied to a field holding several values checks each of them, while a
 * whole-value constraint such as cardinality sees the list.
 */
public record ConstraintSpec(
        String propertyPath,
        String constraintId,
        Map<String, Object> options,
        boolean perItem) {

    public static ConstraintSpec on(String propertyPath, String constraintId) {
        return new ConstraintSpec(propertyPath, constraintId, Map.of(), false);
    }

    public static ConstraintSpec on(String propertyPath, String constraintId, Map<String, Object> options) {
        return new ConstraintSpec(propertyPath, constraintId, options, false);
    }

    public ConstraintSpec forEachItem() {
        return new ConstraintSpec(propertyPath, constraintId, options, true);
    }

    public ConstraintSpec at(String newPropertyPath) {
        return new ConstraintSpec(newPropertyPath, constraintId, options, perItem);
    }
}
