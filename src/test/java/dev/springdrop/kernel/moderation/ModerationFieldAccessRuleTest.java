package dev.springdrop.kernel.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.FieldAccessRule;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.user.UserEntityType;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModerationFieldAccessRuleTest {

    private static final EntityType NODE = NodeEntityType.definition();

    private final ContentModerationService moderations = mock(ContentModerationService.class);

    private final ModerationFieldAccessRule rule = new ModerationFieldAccessRule(moderations);

    private static EntityData node(String bundle) {
        return EntityData.of(NodeEntityType.ID, 3L, bundle, "Spring schedule", Map.of());
    }

    @Test
    void theWorkflowStateIsNeverEditedAsAField() {
        assertThat(rule.check(NODE, NodeEntityType.MODERATION_STATE, FieldAccessRule.EDIT, node("page"), null)
                .forbidden()).isTrue();
    }

    @Test
    void aModeratedNodesPublishedFlagIsNotEdited() {
        when(moderations.moderationOf("article")).thenReturn(Optional.of(mock(Moderation.class)));

        assertThat(rule.check(NODE, BaseFieldDefinition.STATUS, FieldAccessRule.EDIT, node("article"), null)
                .forbidden()).isTrue();
    }

    @Test
    void anUnmoderatedNodesPublishedFlagIsLeftToOtherRules() {
        when(moderations.moderationOf("page")).thenReturn(Optional.empty());

        assertThat(rule.check(NODE, BaseFieldDefinition.STATUS, FieldAccessRule.EDIT, node("page"), null)
                .forbidden()).isFalse();
        assertThat(rule.check(NODE, NodeEntityType.STICKY, FieldAccessRule.EDIT, node("page"), null)
                .forbidden()).isFalse();
    }

    @Test
    void viewingAndOtherEntityTypesAreLeftToOtherRules() {
        assertThat(rule.check(NODE, NodeEntityType.MODERATION_STATE, FieldAccessRule.VIEW, node("page"), null)
                .forbidden()).isFalse();
        assertThat(rule.check(UserEntityType.definition(), NodeEntityType.MODERATION_STATE, FieldAccessRule.EDIT,
                node("page"), null).forbidden()).isFalse();
    }
}
