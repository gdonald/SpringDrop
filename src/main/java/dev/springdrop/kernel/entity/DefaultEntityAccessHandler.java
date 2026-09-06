package dev.springdrop.kernel.entity;

import dev.springdrop.kernel.access.AccessResult;
import dev.springdrop.kernel.access.RouteAccessChecker;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Grants an operation to a user holding {@code administer <entity type>}, and
 * says nothing about anyone else, leaving the answer to the type's own rules. A
 * type with no rules of its own is therefore closed to everyone but its
 * administrators, since nothing else allows anything.
 */
@Component
public class DefaultEntityAccessHandler implements EntityAccessHandler {

    public static String administerPermission(EntityType type) {
        return "administer " + type.id();
    }

    @Override
    public AccessResult check(EntityType type, Object entity, String operation, Authentication authentication) {
        String permission = administerPermission(type);
        boolean granted = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(permission));
        return (granted ? AccessResult.allow() : AccessResult.neutral())
                .withCacheContext(RouteAccessChecker.USER_PERMISSIONS_CONTEXT);
    }
}
