package dev.springdrop.kernel.entity;

import dev.springdrop.kernel.access.AccessResult;
import org.springframework.security.core.Authentication;

/**
 * Decides whether a user may perform an operation on an entity. An entity type
 * names its own handler, so a type with rules of its own (a node's published
 * state, say) replaces the default without core knowing about it.
 */
public interface EntityAccessHandler {

    String VIEW = "view";

    String UPDATE = "update";

    String DELETE = "delete";

    String CREATE = "create";

    AccessResult check(EntityType type, Object entity, String operation, Authentication authentication);
}
