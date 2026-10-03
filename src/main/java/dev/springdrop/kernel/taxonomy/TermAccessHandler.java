package dev.springdrop.kernel.taxonomy;

import dev.springdrop.kernel.access.AccessResult;
import dev.springdrop.kernel.access.RouteAccessChecker;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.node.NodePermissions;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Who may read and manage terms. A published term is open to anyone holding
 * {@code access content}. Creating, editing, and deleting go by the term's
 * vocabulary, and creating is asked about a vocabulary's id rather than a term.
 * Holding {@code administer taxonomy} allows everything.
 */
@Component
public class TermAccessHandler implements EntityAccessHandler {

    @Override
    public AccessResult check(EntityType type, Object entity, String operation, Authentication authentication) {
        Set<String> permissions = (authentication == null) ? Set.of() : authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toSet());
        return decide(entity, operation, permissions).withCacheContext(RouteAccessChecker.USER_PERMISSIONS_CONTEXT);
    }

    private static AccessResult decide(Object entity, String operation, Set<String> permissions) {
        if (permissions.contains(TaxonomyPermissions.ADMINISTER_TAXONOMY)) {
            return AccessResult.allow();
        }
        if (operation.equals(CREATE)) {
            return (entity instanceof String vocabulary)
                    ? grantedBy(permissions, TaxonomyPermissions.create(vocabulary))
                    : AccessResult.neutral();
        }
        if (!(entity instanceof EntityData term)) {
            return AccessResult.neutral();
        }
        return switch (operation) {
            case VIEW -> Boolean.TRUE.equals(term.fields().get(BaseFieldDefinition.STATUS))
                    ? grantedBy(permissions, NodePermissions.ACCESS_CONTENT)
                    : AccessResult.neutral();
            case UPDATE -> grantedBy(permissions, TaxonomyPermissions.edit(term.bundle()));
            case DELETE -> grantedBy(permissions, TaxonomyPermissions.delete(term.bundle()));
            default -> AccessResult.neutral();
        };
    }

    private static AccessResult grantedBy(Set<String> permissions, String permission) {
        return permissions.contains(permission) ? AccessResult.allow() : AccessResult.neutral();
    }
}
