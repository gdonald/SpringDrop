package dev.springdrop.kernel.node;

import dev.springdrop.kernel.access.AccessResult;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.FieldAccessRule;
import dev.springdrop.kernel.user.AccountPrincipals;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Only someone who administers nodes edits a node's published, promoted, and
 * sticky flags, the same people the node form shows those boxes to.
 */
@Component
public class NodeFieldAccessRule implements FieldAccessRule {

    static final Set<String> ADMINISTRATIVE = Set.of(BaseFieldDefinition.STATUS, NodeEntityType.PROMOTE,
            NodeEntityType.STICKY);

    public static boolean administersNodes(Authentication who) {
        return AccountPrincipals.bypassesChecks(who) || holds(who, NodePermissions.ADMINISTER_NODES)
                || holds(who, NodePermissions.BYPASS_NODE_ACCESS);
    }

    private static boolean holds(Authentication who, String permission) {
        return who != null && who.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(permission));
    }

    @Override
    public AccessResult check(EntityType type, String fieldName, String operation, EntityData entity,
            Authentication authentication) {
        if (!type.id().equals(NodeEntityType.ID) || !operation.equals(EDIT) || !ADMINISTRATIVE.contains(fieldName)
                || administersNodes(authentication)) {
            return AccessResult.neutral();
        }
        return AccessResult.forbid("The " + NodePermissions.ADMINISTER_NODES + " permission is required to edit "
                + fieldName + ".");
    }
}
