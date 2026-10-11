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

/** Saves a node as it is, which stamps it as changed and runs everything that reacts to a save. */
@SpringDropPlugin(id = SaveNode.ID, type = Action.class)
public class SaveNode implements Action {

    public static final String ID = "node_save";

    private final NodeService nodes;
    private final EntityAccessManager access;

    public SaveNode(NodeService nodes, EntityAccessManager access) {
        this.nodes = nodes;
        this.access = access;
    }

    @Override
    public String label() {
        return "Save content";
    }

    @Override
    public String entityType() {
        return NodeEntityType.ID;
    }

    @Override
    public AccessResult access(EntityData entity, Authentication who) {
        return access.check(NodeEntityType.ID, entity, EntityAccessHandler.UPDATE, who);
    }

    @Override
    public void execute(EntityData entity, Map<String, Object> configuration, Authentication who) {
        nodes.save(entity, Accounts.idOf(who));
    }
}
