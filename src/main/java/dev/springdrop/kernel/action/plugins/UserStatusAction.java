package dev.springdrop.kernel.action.plugins;

import dev.springdrop.kernel.access.AccessResult;
import dev.springdrop.kernel.action.Action;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.user.AccountPrincipals;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.user.UserEntityType;
import org.springframework.security.core.Authentication;

/**
 * An action that blocks or unblocks an account. It is open to someone who
 * administers users, and never applies to the anonymous account, which is
 * not one anyone signs in to.
 */
abstract class UserStatusAction implements Action {

    @Override
    public String entityType() {
        return UserEntityType.ID;
    }

    @Override
    public AccessResult access(EntityData entity, Authentication who) {
        if (((Number) entity.id()).longValue() == UserAccount.ANONYMOUS_ID) {
            return AccessResult.forbid("The anonymous account cannot be blocked or unblocked.");
        }
        boolean administers = AccountPrincipals.bypassesChecks(who) || who.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(Permissions.ADMINISTER_USERS));
        return administers ? AccessResult.allow() : AccessResult.neutral();
    }
}
