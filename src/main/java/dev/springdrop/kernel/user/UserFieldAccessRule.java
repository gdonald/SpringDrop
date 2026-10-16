package dev.springdrop.kernel.user;

import dev.springdrop.kernel.access.AccessResult;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.FieldAccessRule;
import dev.springdrop.kernel.security.Permissions;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * No one views or edits an account's password hash. An account's email address
 * is seen and changed by the account itself and by those who administer users,
 * and only they change whether an account is active and which roles it holds.
 */
@Component
public class UserFieldAccessRule implements FieldAccessRule {

    static final Set<String> ADMINISTRATIVE = Set.of(BaseFieldDefinition.STATUS, UserEntityType.ROLES);

    @Override
    public AccessResult check(EntityType type, String fieldName, String operation, EntityData entity,
            Authentication authentication) {
        if (!type.id().equals(UserEntityType.ID)) {
            return AccessResult.neutral();
        }
        if (fieldName.equals(UserEntityType.PASSWORD_HASH)) {
            return AccessResult.forbid("The password hash is never sent or written.");
        }
        if (fieldName.equals(UserEntityType.MAIL) && !administersUsers(authentication)
                && !ownAccount(entity, authentication)) {
            return AccessResult.forbid("Only the account itself and user administrators reach its email address.");
        }
        if (operation.equals(EDIT) && ADMINISTRATIVE.contains(fieldName) && !administersUsers(authentication)) {
            return AccessResult.forbid("The " + Permissions.ADMINISTER_USERS + " permission is required to edit "
                    + fieldName + ".");
        }
        return AccessResult.neutral();
    }

    private static boolean administersUsers(Authentication who) {
        return AccountPrincipals.bypassesChecks(who) || (who != null && who.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(Permissions.ADMINISTER_USERS)));
    }

    private static boolean ownAccount(EntityData account, Authentication who) {
        return who != null && who.getPrincipal() instanceof AccountPrincipal principal && account.id() != null
                && ((Number) account.id()).longValue() == principal.id();
    }
}
