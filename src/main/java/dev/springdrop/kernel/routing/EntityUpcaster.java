package dev.springdrop.kernel.routing;

import java.util.Optional;

/**
 * Loads an entity of type {@code T} from a route's path value. A module provides
 * one per entity type it wants upcast in routes (the SpringDrop analogue of
 * Drupal's route parameter upcasting).
 */
public interface EntityUpcaster<T> {

    Class<T> entityType();

    Optional<T> load(String id);
}
