package dev.springdrop.kernel.entity;

import dev.springdrop.kernel.access.AccessResult;
import org.springframework.security.core.Authentication;

/**
 * One module's say on whether an operation may go ahead. A rule that has no
 * opinion says nothing, so several modules can be asked in turn: one forbidding
 * settles it, and one allowing is enough when nobody forbids.
 */
@FunctionalInterface
public interface EntityAccessRule {

    AccessResult check(EntityType type, Object entity, String operation, Authentication authentication);
}
