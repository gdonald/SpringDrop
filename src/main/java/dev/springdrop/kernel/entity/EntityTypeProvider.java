package dev.springdrop.kernel.entity;

import java.util.List;

/**
 * Contributes entity type definitions. A module registers one as a bean, the
 * same way it contributes routes, and the {@link EntityTypeManager} gathers them
 * at startup.
 */
@FunctionalInterface
public interface EntityTypeProvider {

    List<EntityType> entityTypes();
}
