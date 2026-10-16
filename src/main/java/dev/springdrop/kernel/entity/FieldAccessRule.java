package dev.springdrop.kernel.entity;

import dev.springdrop.kernel.access.AccessResult;
import org.springframework.security.core.Authentication;

/**
 * One module's say on whether someone may view or edit one field of an entity.
 * A rule with no opinion says nothing. A field is open unless a rule forbids it,
 * since whoever reached the entity already passed its entity access.
 */
@FunctionalInterface
public interface FieldAccessRule {

    String VIEW = "view";

    String EDIT = "edit";

    AccessResult check(EntityType type, String fieldName, String operation, EntityData entity,
            Authentication authentication);
}
