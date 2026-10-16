package dev.springdrop.kernel.entity;

import dev.springdrop.kernel.access.AccessResult;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Whether someone may view or edit one field of an entity. Every rule is asked,
 * and the first that forbids settles it. A field no rule forbids is allowed.
 * The first account is asked about like anyone else, so a field no one may see,
 * such as a password hash, stays hidden from it too.
 */
@Component
public class FieldAccessManager {

    private final List<FieldAccessRule> rules;

    public FieldAccessManager(List<FieldAccessRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public AccessResult check(EntityType type, String fieldName, String operation, EntityData entity,
            Authentication authentication) {
        for (FieldAccessRule rule : rules) {
            AccessResult result = rule.check(type, fieldName, operation, entity, authentication);
            if (result.forbidden()) {
                return result;
            }
        }
        return AccessResult.allow();
    }

    public boolean may(EntityType type, String fieldName, String operation, EntityData entity,
            Authentication authentication) {
        return check(type, fieldName, operation, entity, authentication).allowed();
    }
}
