package dev.springdrop.kernel.media;

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
 * Who may view, change, and remove media. Published media are open to anyone
 * holding {@code view media}, and unpublished media to their owner when the
 * owner may view their own. Editing and deleting go by the media type: any
 * media of it, or the ones the person owns. Creating is asked about a media
 * type's id. Holding {@code administer media} allows everything.
 */
@Component
public class MediaAccessHandler implements EntityAccessHandler {

    @Override
    public AccessResult check(EntityType type, Object entity, String operation, Authentication authentication) {
        Set<String> permissions = (authentication == null) ? Set.of() : authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority()).collect(Collectors.toSet());
        return decide(entity, operation, permissions, authentication)
                .withCacheContext(RouteAccessChecker.USER_PERMISSIONS_CONTEXT);
    }

    private static AccessResult decide(Object entity, String operation, Set<String> permissions,
            Authentication authentication) {
        if (permissions.contains(MediaPermissions.ADMINISTER_MEDIA)) {
            return AccessResult.allow();
        }
        if (operation.equals(CREATE)) {
            return (entity instanceof String type) ? granted(permissions.contains(MediaPermissions.create(type)))
                    : AccessResult.neutral();
        }
        if (!(entity instanceof EntityData media)) {
            return AccessResult.neutral();
        }
        boolean owns = authentication != null && authentication.getPrincipal() instanceof AccountPrincipal account
                && media.fields().get(BaseFieldDefinition.OWNER) instanceof Number owner
                && owner.longValue() == account.id();
        return switch (operation) {
            case VIEW -> Boolean.TRUE.equals(media.fields().get(BaseFieldDefinition.STATUS))
                    ? granted(permissions.contains(MediaPermissions.VIEW_MEDIA))
                    : granted(owns && permissions.contains(MediaPermissions.VIEW_OWN_UNPUBLISHED));
            case UPDATE -> granted(permissions.contains(MediaPermissions.editAny(media.bundle()))
                    || owns && permissions.contains(MediaPermissions.editOwn(media.bundle())));
            case DELETE -> granted(permissions.contains(MediaPermissions.deleteAny(media.bundle()))
                    || owns && permissions.contains(MediaPermissions.deleteOwn(media.bundle())));
            default -> AccessResult.neutral();
        };
    }

    private static AccessResult granted(boolean allowed) {
        return allowed ? AccessResult.allow() : AccessResult.neutral();
    }
}
