package dev.springdrop.kernel.node;

import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.user.AccountPrincipals;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Who may read, revert, and delete a node's revisions. Each takes the matching
 * revision permission, for the node's content type or for every type, on top
 * of being allowed to read, edit, or delete the node itself. Holding
 * {@code bypass node access}, or being the first account, covers the revision
 * permission.
 */
@Component
public class NodeRevisionAccess {

    private final EntityAccessManager entityAccess;

    public NodeRevisionAccess(EntityAccessManager entityAccess) {
        this.entityAccess = entityAccess;
    }

    public boolean mayView(EntityData node) {
        return holdsEither(NodePermissions.VIEW_ALL_REVISIONS, NodePermissions.viewRevisions(node.bundle()))
                && entityAccess.may(NodeEntityType.ID, node, EntityAccessHandler.VIEW);
    }

    public boolean mayRevert(EntityData node) {
        return holdsEither(NodePermissions.REVERT_ALL_REVISIONS, NodePermissions.revertRevisions(node.bundle()))
                && entityAccess.may(NodeEntityType.ID, node, EntityAccessHandler.UPDATE);
    }

    public boolean mayDelete(EntityData node) {
        return holdsEither(NodePermissions.DELETE_ALL_REVISIONS, NodePermissions.deleteRevisions(node.bundle()))
                && entityAccess.may(NodeEntityType.ID, node, EntityAccessHandler.DELETE);
    }

    /** Reading a revision ahead of the live one takes {@code view latest version} and being allowed to edit the node. */
    public boolean mayViewLatestVersion(EntityData node) {
        return holdsEither(NodePermissions.VIEW_LATEST_VERSION, NodePermissions.VIEW_LATEST_VERSION)
                && entityAccess.may(NodeEntityType.ID, node, EntityAccessHandler.UPDATE);
    }

    private static boolean holdsEither(String forEveryType, String forThisType) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return AccountPrincipals.bypassesChecks(authentication) || authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .anyMatch(permission -> permission.equals(forEveryType) || permission.equals(forThisType)
                        || permission.equals(NodePermissions.BYPASS_NODE_ACCESS));
    }
}
