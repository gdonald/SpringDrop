package dev.springdrop.kernel.moderation;

import dev.springdrop.kernel.access.AccessResult;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.FieldAccessRule;
import dev.springdrop.kernel.node.NodeEntityType;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * A node's workflow state is moved only by its transitions, so no one edits it
 * as a field, and a node a workflow moderates is published by moving it into a
 * published state, so no one edits its published flag either.
 */
@Component
public class ModerationFieldAccessRule implements FieldAccessRule {

    private final ContentModerationService moderations;

    public ModerationFieldAccessRule(ContentModerationService moderations) {
        this.moderations = moderations;
    }

    @Override
    public AccessResult check(EntityType type, String fieldName, String operation, EntityData entity,
            Authentication authentication) {
        if (!type.id().equals(NodeEntityType.ID) || !operation.equals(EDIT)) {
            return AccessResult.neutral();
        }
        if (fieldName.equals(NodeEntityType.MODERATION_STATE)) {
            return AccessResult.forbid("The workflow state changes only through a transition.");
        }
        if (fieldName.equals(BaseFieldDefinition.STATUS) && moderations.moderationOf(entity.bundle()).isPresent()) {
            return AccessResult.forbid("A moderated node is published through its workflow.");
        }
        return AccessResult.neutral();
    }
}
