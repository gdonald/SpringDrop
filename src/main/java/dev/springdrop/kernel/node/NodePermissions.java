package dev.springdrop.kernel.node;

import dev.springdrop.kernel.permission.PermissionDefinition;
import dev.springdrop.kernel.permission.PermissionProvider;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The permissions each content type adds: creating its nodes, editing and
 * deleting them, either one's own or anyone's, and viewing, reverting, and
 * deleting their revisions. They are built from the types
 * the site has, so a new type offers its permissions as soon as it is saved.
 */
@Component
public class NodePermissions implements PermissionProvider {

    public static final String PROVIDER = "node";

    /** Reading published content. */
    public static final String ACCESS_CONTENT = "access content";

    /** Adding content types and changing what they are set up as. */
    public static final String ADMINISTER_CONTENT_TYPES = "administer content types";

    /** Viewing, editing, and deleting every node, published or not. */
    public static final String BYPASS_NODE_ACCESS = "bypass node access";

    /** Reading one's own nodes while they are unpublished. */
    public static final String VIEW_OWN_UNPUBLISHED = "view own unpublished content";

    /** Reading any node while it is unpublished, whoever wrote it. */
    public static final String VIEW_ANY_UNPUBLISHED = "view any unpublished content";

    /** Reading the newest revision of a node when it is ahead of the one the site shows. */
    public static final String VIEW_LATEST_VERSION = "view latest version";

    /** Reading the revisions of every content type's nodes. */
    public static final String VIEW_ALL_REVISIONS = "view all revisions";

    /** Reverting the nodes of every content type to an earlier revision. */
    public static final String REVERT_ALL_REVISIONS = "revert all revisions";

    /** Deleting earlier revisions of every content type's nodes. */
    public static final String DELETE_ALL_REVISIONS = "delete all revisions";

    private final NodeTypeManager types;

    public NodePermissions(NodeTypeManager types) {
        this.types = types;
    }

    public static String create(String type) {
        return "create " + type + " content";
    }

    public static String editOwn(String type) {
        return "edit own " + type + " content";
    }

    public static String editAny(String type) {
        return "edit any " + type + " content";
    }

    public static String deleteOwn(String type) {
        return "delete own " + type + " content";
    }

    public static String deleteAny(String type) {
        return "delete any " + type + " content";
    }

    public static String viewRevisions(String type) {
        return "view " + type + " revisions";
    }

    public static String revertRevisions(String type) {
        return "revert " + type + " revisions";
    }

    public static String deleteRevisions(String type) {
        return "delete " + type + " revisions";
    }

    @Override
    public List<PermissionDefinition> permissions() {
        List<PermissionDefinition> permissions = new ArrayList<>();
        for (NodeType type : types.all()) {
            String label = type.label();
            permissions.add(PermissionDefinition.of(create(type.id()), label + ": Create new content", PROVIDER));
            permissions.add(PermissionDefinition.of(editOwn(type.id()), label + ": Edit own content", PROVIDER));
            permissions.add(PermissionDefinition.of(editAny(type.id()), label + ": Edit any content", PROVIDER));
            permissions.add(PermissionDefinition.of(deleteOwn(type.id()), label + ": Delete own content", PROVIDER));
            permissions.add(PermissionDefinition.of(deleteAny(type.id()), label + ": Delete any content", PROVIDER));
            permissions.add(PermissionDefinition.of(viewRevisions(type.id()), label + ": View revisions", PROVIDER));
            permissions.add(PermissionDefinition.of(revertRevisions(type.id()), label + ": Revert revisions", PROVIDER));
            permissions.add(PermissionDefinition.of(deleteRevisions(type.id()), label + ": Delete revisions", PROVIDER));
        }
        return List.copyOf(permissions);
    }
}
