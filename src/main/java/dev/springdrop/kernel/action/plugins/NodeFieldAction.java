package dev.springdrop.kernel.action.plugins;

import dev.springdrop.kernel.access.AccessResult;
import dev.springdrop.kernel.action.Action;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.moderation.ContentModerationService;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.Authentication;

/**
 * An action that sets one yes-or-no field of a node, such as whether it is
 * published, and saves it. It is open to someone who may change the node.
 * Publishing or unpublishing a node whose content type is moderated goes
 * through its workflow instead, so those actions refuse such a node.
 */
abstract class NodeFieldAction implements Action {

    private final NodeService nodes;
    private final EntityAccessManager access;
    private final ContentModerationService moderation;

    NodeFieldAction(NodeService nodes, EntityAccessManager access, ContentModerationService moderation) {
        this.nodes = nodes;
        this.access = access;
        this.moderation = moderation;
    }

    /** The field the action sets. */
    abstract String field();

    /** The value the action sets the field to. */
    abstract boolean value();

    @Override
    public String entityType() {
        return NodeEntityType.ID;
    }

    @Override
    public AccessResult access(EntityData entity, Authentication who) {
        if (field().equals(BaseFieldDefinition.STATUS) && moderation.moderationOf(entity.bundle()).isPresent()) {
            return AccessResult.forbid("Content under a workflow is published by moving it between states.");
        }
        return access.check(NodeEntityType.ID, entity, EntityAccessHandler.UPDATE, who);
    }

    @Override
    public void execute(EntityData entity, Map<String, Object> configuration, Authentication who) {
        Map<String, Object> fields = new LinkedHashMap<>(entity.fields());
        fields.put(field(), value());
        nodes.save(entity.withFields(fields), Accounts.idOf(who));
    }
}
