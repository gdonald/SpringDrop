package dev.springdrop.kernel.validation;

import java.util.Map;
import java.util.Optional;

/**
 * A reusable check attached to a value. A constraint is a plugin, registered
 * with {@code @SpringDropPlugin(type = Constraint.class)}, so a module adds one
 * without core knowing about it. It returns the violation message when the value
 * fails, and nothing when it passes. Options come from the attachment, which lets
 * one constraint serve many fields with different bounds.
 */
public interface Constraint {

    Optional<String> validate(Object value, Map<String, Object> options, ValidationContext context);
}
