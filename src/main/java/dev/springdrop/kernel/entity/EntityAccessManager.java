package dev.springdrop.kernel.entity;

import dev.springdrop.kernel.access.AccessResult;
import dev.springdrop.kernel.access.RouteAccessChecker;
import dev.springdrop.kernel.user.AccountPrincipals;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Whether someone may view, change, delete, or create an entity. The type's own
 * handler answers first, then every module rule is asked, so a module can allow
 * something the type would not, and any of them can forbid it outright. The
 * first account is not asked about at all.
 */
@Component
public class EntityAccessManager {

    private final EntityTypeManager entityTypeManager;
    private final List<EntityAccessRule> rules;

    public EntityAccessManager(EntityTypeManager entityTypeManager, List<EntityAccessRule> rules) {
        this.entityTypeManager = entityTypeManager;
        this.rules = rules;
    }

    /** The decision for the person making the current request. */
    public AccessResult check(String entityTypeId, Object entity, String operation) {
        return check(entityTypeId, entity, operation,
                SecurityContextHolder.getContext().getAuthentication());
    }

    public AccessResult check(
            String entityTypeId, Object entity, String operation, Authentication authentication) {

        EntityType type = entityTypeManager.require(entityTypeId);
        if (AccountPrincipals.bypassesChecks(authentication)) {
            return AccessResult.allow()
                    .because("The first account is not subject to access checks")
                    .withCacheContext(RouteAccessChecker.USER_PERMISSIONS_CONTEXT);
        }

        AccessResult result = entityTypeManager.accessHandlerFor(entityTypeId)
                .check(type, entity, operation, authentication);
        for (EntityAccessRule rule : rules) {
            result = result.or(rule.check(type, entity, operation, authentication));
        }
        return result;
    }

    public boolean may(String entityTypeId, Object entity, String operation) {
        return check(entityTypeId, entity, operation).allowed();
    }
}
