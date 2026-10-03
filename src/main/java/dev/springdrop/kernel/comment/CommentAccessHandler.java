package dev.springdrop.kernel.comment;

import dev.springdrop.kernel.access.AccessResult;
import dev.springdrop.kernel.access.RouteAccessChecker;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.user.AccountPrincipal;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Who may read and manage comments. A published comment is open to anyone
 * holding {@code access comments}. Its author may change it while holding
 * {@code edit own comments}. Creating takes {@code post comments}. Holding
 * {@code administer comments} allows everything, including reading comments
 * waiting for approval and deleting any comment.
 */
@Component
public class CommentAccessHandler implements EntityAccessHandler {

    @Override
    public AccessResult check(EntityType type, Object entity, String operation, Authentication authentication) {
        Set<String> permissions = (authentication == null) ? Set.of() : authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toSet());
        return decide(entity, operation, permissions, authentication)
                .withCacheContext(RouteAccessChecker.USER_PERMISSIONS_CONTEXT);
    }

    private static AccessResult decide(
            Object entity, String operation, Set<String> permissions, Authentication authentication) {
        if (permissions.contains(CommentPermissions.ADMINISTER_COMMENTS)) {
            return AccessResult.allow();
        }
        if (operation.equals(CREATE)) {
            return grantedBy(permissions.contains(CommentPermissions.POST_COMMENTS));
        }
        if (!(entity instanceof EntityData comment)) {
            return AccessResult.neutral();
        }
        boolean published = Boolean.TRUE.equals(comment.fields().get(BaseFieldDefinition.STATUS));
        return switch (operation) {
            case VIEW -> grantedBy(published && permissions.contains(CommentPermissions.ACCESS_COMMENTS));
            case UPDATE -> grantedBy(ownedBy(comment, authentication) && permissions.contains(
                    CommentPermissions.EDIT_OWN)).withCacheContext("user");
            default -> AccessResult.neutral();
        };
    }

    private static boolean ownedBy(EntityData comment, Authentication authentication) {
        return authentication != null
                && authentication.getPrincipal() instanceof AccountPrincipal account
                && comment.fields().get(BaseFieldDefinition.OWNER) instanceof Number owner
                && owner.longValue() == account.id();
    }

    private static AccessResult grantedBy(boolean granted) {
        return granted ? AccessResult.allow() : AccessResult.neutral();
    }
}
