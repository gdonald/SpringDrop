package dev.springdrop.kernel.access;

import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The access checks a template asks, so a control is left out rather than shown
 * and then refused. Templates reach it as {@code ${@access.has('...')}} and
 * {@code ${@access.mayUpdate('node', node)}}.
 */
@Component("access")
public class AccessHelper {

    private final EntityAccessManager entityAccess;

    public AccessHelper(EntityAccessManager entityAccess) {
        this.entityAccess = entityAccess;
    }

    /** Whether the person making this request holds a permission. */
    public boolean has(String permission) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(permission));
    }

    public boolean mayView(String entityTypeId, Object entity) {
        return entityAccess.may(entityTypeId, entity, EntityAccessHandler.VIEW);
    }

    public boolean mayUpdate(String entityTypeId, Object entity) {
        return entityAccess.may(entityTypeId, entity, EntityAccessHandler.UPDATE);
    }

    public boolean mayDelete(String entityTypeId, Object entity) {
        return entityAccess.may(entityTypeId, entity, EntityAccessHandler.DELETE);
    }

    public boolean mayCreate(String entityTypeId) {
        return entityAccess.may(entityTypeId, null, EntityAccessHandler.CREATE);
    }
}
