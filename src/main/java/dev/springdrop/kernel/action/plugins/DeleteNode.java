package dev.springdrop.kernel.action.plugins;

import dev.springdrop.kernel.access.AccessResult;
import dev.springdrop.kernel.action.Action;
import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import java.util.Map;
import org.springframework.security.core.Authentication;

/** Deletes a node. It cannot be undone, so applying it to a selection asks for confirmation. */
@SpringDropPlugin(id = DeleteNode.ID, type = Action.class)
public class DeleteNode implements Action {

    public static final String ID = "node_delete";

    private final NodeService nodes;
    private final EntityAccessManager access;

    public DeleteNode(NodeService nodes, EntityAccessManager access) {
        this.nodes = nodes;
        this.access = access;
    }

    @Override
    public String label() {
        return "Delete content";
    }

    @Override
    public String entityType() {
        return NodeEntityType.ID;
    }

    @Override
    public boolean destructive() {
        return true;
    }

    @Override
    public AccessResult access(EntityData entity, Authentication who) {
        return access.check(NodeEntityType.ID, entity, EntityAccessHandler.DELETE, who);
    }

    @Override
    public void execute(EntityData entity, Map<String, Object> configuration, Authentication who) {
        nodes.delete(((Number) entity.id()).longValue());
    }
}
