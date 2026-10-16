package dev.springdrop.kernel.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.FieldAccessRule;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.user.UserEntityType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

class NodeFieldAccessRuleTest {

    private static final EntityType NODE = NodeEntityType.definition();

    private static final EntityData ARTICLE = EntityData.of(NodeEntityType.ID, 3L, "article", "Spring schedule",
            Map.of());

    private final NodeFieldAccessRule rule = new NodeFieldAccessRule();

    private static Authentication account(long id, String... permissions) {
        AccountPrincipal principal = new AccountPrincipal(id, "account" + id, "", true, List.of(permissions));
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    private boolean forbidden(EntityType type, String field, String operation, Authentication who) {
        return rule.check(type, field, operation, ARTICLE, who).forbidden();
    }

    @Test
    void editingThePublishingFlagsNeedsTheAdministerNodesPermission() {
        assertThat(forbidden(NODE, BaseFieldDefinition.STATUS, FieldAccessRule.EDIT, account(7L))).isTrue();
        assertThat(forbidden(NODE, NodeEntityType.PROMOTE, FieldAccessRule.EDIT, account(7L))).isTrue();
        assertThat(forbidden(NODE, NodeEntityType.STICKY, FieldAccessRule.EDIT,
                account(7L, NodePermissions.ADMINISTER_NODES))).isFalse();
    }

    @Test
    void bypassingNodeAccessOrBeingTheFirstAccountAlsoEditsThePublishingFlags() {
        assertThat(forbidden(NODE, NodeEntityType.STICKY, FieldAccessRule.EDIT,
                account(7L, NodePermissions.BYPASS_NODE_ACCESS))).isFalse();
        assertThat(forbidden(NODE, NodeEntityType.STICKY, FieldAccessRule.EDIT,
                account(UserAccount.ADMINISTRATOR_ID))).isFalse();
    }

    @Test
    void viewingThePublishingFlagsOrEditingOtherFieldsIsLeftToOtherRules() {
        assertThat(forbidden(NODE, NodeEntityType.STICKY, FieldAccessRule.VIEW, account(7L))).isFalse();
        assertThat(forbidden(NODE, "summary", FieldAccessRule.EDIT, account(7L))).isFalse();
        assertThat(forbidden(UserEntityType.definition(), BaseFieldDefinition.STATUS, FieldAccessRule.EDIT,
                account(7L))).isFalse();
    }

    @Test
    void someoneSignedOutDoesNotAdministerNodes() {
        assertThat(NodeFieldAccessRule.administersNodes(null)).isFalse();
    }
}
