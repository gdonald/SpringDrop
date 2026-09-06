package dev.springdrop.kernel.entity;

import dev.springdrop.kernel.validation.ConstraintSpec;
import java.util.List;

/**
 * Contributes the constraints an entity of a bundle must satisfy. The Field API
 * supplies them from field configuration; a module can add its own by
 * registering another provider.
 */
@FunctionalInterface
public interface EntityConstraintProvider {

    List<ConstraintSpec> constraintsFor(EntityType type, String bundle);
}
