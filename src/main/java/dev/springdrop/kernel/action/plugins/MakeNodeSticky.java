package dev.springdrop.kernel.action.plugins;

import dev.springdrop.kernel.action.Action;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.moderation.ContentModerationService;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.plugin.SpringDropPlugin;

@SpringDropPlugin(id = MakeNodeSticky.ID, type = Action.class)
public class MakeNodeSticky extends NodeFieldAction {

    public static final String ID = "node_make_sticky";

    public MakeNodeSticky(NodeService nodes, EntityAccessManager access, ContentModerationService moderation) {
        super(nodes, access, moderation);
    }

    @Override
    public String label() {
        return "Make content sticky";
    }

    @Override
    String field() {
        return NodeEntityType.STICKY;
    }

    @Override
    boolean value() {
        return true;
    }
}
