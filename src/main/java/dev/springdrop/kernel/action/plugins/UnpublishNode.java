package dev.springdrop.kernel.action.plugins;

import dev.springdrop.kernel.action.Action;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.moderation.ContentModerationService;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.plugin.SpringDropPlugin;

@SpringDropPlugin(id = UnpublishNode.ID, type = Action.class)
public class UnpublishNode extends NodeFieldAction {

    public static final String ID = "node_unpublish";

    public UnpublishNode(NodeService nodes, EntityAccessManager access, ContentModerationService moderation) {
        super(nodes, access, moderation);
    }

    @Override
    public String label() {
        return "Unpublish content";
    }

    @Override
    String field() {
        return BaseFieldDefinition.STATUS;
    }

    @Override
    boolean value() {
        return false;
    }
}
