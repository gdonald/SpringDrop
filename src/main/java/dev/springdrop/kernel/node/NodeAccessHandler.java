package dev.springdrop.kernel.node;

import dev.springdrop.kernel.access.AccessResult;
import dev.springdrop.kernel.access.RouteAccessChecker;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.user.AccountPrincipal;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Who may read, write, and remove nodes. Published nodes are open to anyone
 * holding {@code access content}, and unpublished ones to someone who may view
 * any unpublished content, or to their owner when the owner may view their own. Editing and deleting go by the
 * content type: any node of it, or the ones the person owns. Creating is asked
 * about a content type's id rather than a node, since there is no node yet.
 *
 * <p>Holding {@code bypass node access} allows everything. An answer that turns
 * on who owns the node varies by the person asking as well as their permissions.
 */
@Component
public class NodeAccessHandler implements EntityAccessHandler {

    public static final String USER_CONTEXT = "user";

    @Override
    public AccessResult check(EntityType type, Object entity, String operation, Authentication authentication) {
        Set<String> permissions = permissionsOf(authentication);
        AccessResult result = decide(entity, operation, permissions, accountId(authentication))
                .withCacheContext(RouteAccessChecker.USER_PERMISSIONS_CONTEXT);
        return (entity instanceof EntityData node) ? result.withCacheTag(NodeService.cacheTag(node.id())) : result;
    }

    private static AccessResult decide(
            Object entity, String operation, Set<String> permissions, Optional<Long> accountId) {

        if (permissions.contains(NodePermissions.BYPASS_NODE_ACCESS)) {
            return AccessResult.allow();
        }
        if (operation.equals(CREATE)) {
            return (entity instanceof String type) ? grantedBy(permissions, NodePermissions.create(type))
                    : AccessResult.neutral();
        }
        if (!(entity instanceof EntityData node)) {
            return AccessResult.neutral();
        }

        boolean owns = accountId.isPresent() && accountId.get().equals(ownerOf(node));
        return switch (operation) {
            case VIEW -> view(node, permissions, owns);
            case UPDATE -> ownOrAny(permissions, owns,
                    NodePermissions.editOwn(node.bundle()), NodePermissions.editAny(node.bundle()));
            case DELETE -> ownOrAny(permissions, owns,
                    NodePermissions.deleteOwn(node.bundle()), NodePermissions.deleteAny(node.bundle()));
            default -> AccessResult.neutral();
        };
    }

    private static AccessResult view(EntityData node, Set<String> permissions, boolean owns) {
        if (Boolean.TRUE.equals(node.fields().get(BaseFieldDefinition.STATUS))) {
            return grantedBy(permissions, NodePermissions.ACCESS_CONTENT);
        }
        if (permissions.contains(NodePermissions.VIEW_ANY_UNPUBLISHED)) {
            return AccessResult.allow();
        }
        boolean ownUnpublished = owns && permissions.contains(NodePermissions.VIEW_OWN_UNPUBLISHED);
        return (ownUnpublished ? AccessResult.allow() : AccessResult.neutral()).withCacheContext(USER_CONTEXT);
    }

    private static AccessResult ownOrAny(Set<String> permissions, boolean owns, String own, String any) {
        if (permissions.contains(any)) {
            return AccessResult.allow();
        }
        boolean ownGranted = owns && permissions.contains(own);
        return (ownGranted ? AccessResult.allow() : AccessResult.neutral()).withCacheContext(USER_CONTEXT);
    }

    private static AccessResult grantedBy(Set<String> permissions, String permission) {
        return permissions.contains(permission) ? AccessResult.allow() : AccessResult.neutral();
    }

    private static Long ownerOf(EntityData node) {
        return (node.fields().get(BaseFieldDefinition.OWNER) instanceof Number owner) ? owner.longValue() : null;
    }

    private static Optional<Long> accountId(Authentication authentication) {
        return (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal account)
                ? Optional.of(account.id())
                : Optional.empty();
    }

    private static Set<String> permissionsOf(Authentication authentication) {
        if (authentication == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toSet());
    }
}
