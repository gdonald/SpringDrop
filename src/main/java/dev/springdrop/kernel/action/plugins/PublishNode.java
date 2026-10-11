package dev.springdrop.kernel.action.plugins;

import dev.springdrop.kernel.action.Action;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.moderation.ContentModerationService;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.plugin.SpringDropPlugin;

@SpringDropPlugin(id = PublishNode.ID, type = Action.class)
public class PublishNode extends NodeFieldAction {

    public static final String ID = "node_publish";

    public PublishNode(NodeService nodes, EntityAccessManager access, ContentModerationService moderation) {
        super(nodes, access, moderation);
    }

    @Override
    public String label() {
        return "Publish content";
    }

    @Override
    String field() {
        return BaseFieldDefinition.STATUS;
    }

    @Override
    boolean value() {
        return true;
    }
}
