package dev.springdrop.kernel.action.plugins;

import dev.springdrop.kernel.action.Action;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.moderation.ContentModerationService;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.plugin.SpringDropPlugin;

@SpringDropPlugin(id = UnpromoteNode.ID, type = Action.class)
public class UnpromoteNode extends NodeFieldAction {

    public static final String ID = "node_unpromote";

    public UnpromoteNode(NodeService nodes, EntityAccessManager access, ContentModerationService moderation) {
        super(nodes, access, moderation);
    }

    @Override
    public String label() {
        return "Remove content from front page";
    }

    @Override
    String field() {
        return NodeEntityType.PROMOTE;
    }

    @Override
    boolean value() {
        return false;
    }
}
