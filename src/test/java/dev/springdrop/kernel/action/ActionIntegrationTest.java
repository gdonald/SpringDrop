package dev.springdrop.kernel.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.springdrop.kernel.action.plugins.AssignNodeOwner;
import dev.springdrop.kernel.action.plugins.BlockUser;
import dev.springdrop.kernel.action.plugins.DeleteNode;
import dev.springdrop.kernel.action.plugins.MakeNodeSticky;
import dev.springdrop.kernel.action.plugins.MakeNodeUnsticky;
import dev.springdrop.kernel.action.plugins.PromoteNode;
import dev.springdrop.kernel.action.plugins.PublishNode;
import dev.springdrop.kernel.action.plugins.SaveNode;
import dev.springdrop.kernel.action.plugins.UnblockUser;
import dev.springdrop.kernel.action.plugins.UnpromoteNode;
import dev.springdrop.kernel.action.plugins.UnpublishNode;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.log.DatabaseLog;
import dev.springdrop.kernel.log.LogEntry;
import dev.springdrop.kernel.log.LogFilter;
import dev.springdrop.kernel.log.Severity;
import dev.springdrop.kernel.moderation.ContentModerationService;
import dev.springdrop.kernel.moderation.ModerationConfig;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.kernel.user.UserEntityType;
import dev.springdrop.kernel.workflow.WorkflowConfig;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

@SpringBootTest
class ActionIntegrationTest extends AbstractIntegrationTest {

    private static final String ARTICLE = "article";

    private static final String NEWS = "news";

    @Autowired
    private ActionManager actions;

    @Autowired
    private NodeService nodes;

    @Autowired
    private NodeTypeManager types;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private ContentModerationService moderations;

    @Autowired
    private ConfigStore config;

    @Autowired
    private DatabaseLog log;

    @BeforeEach
    void articlesAndAnEditor() {
        accounts.install();
        clear();
        types.save(NodeType.of(ARTICLE, "Article"));
        types.save(NodeType.of(NEWS, "News"));
        log.clear();
    }

    @AfterEach
    void nothingLeft() {
        clear();
    }

    private void clear() {
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        types.all().forEach(type -> types.delete(type.id()));
        config.listNames(ActionConfig.CONFIG_PREFIX).forEach(config::delete);
        config.listNames(WorkflowConfig.CONFIG_PREFIX).forEach(config::delete);
        config.listNames(ModerationConfig.CONFIG_PREFIX).forEach(config::delete);
        List.of("ada", "grace").forEach(name ->
                accounts.findByName(name).ifPresent(account -> entities.delete(UserEntityType.ID, account.id())));
    }

    private static Authentication account(long id, String... permissions) {
        AccountPrincipal principal = new AccountPrincipal(id, "editor" + id, "", true, List.of(permissions));
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    private static Authentication articleEditor() {
        return account(5L, NodePermissions.editAny(ARTICLE), NodePermissions.deleteAny(ARTICLE));
    }

    private EntityData article(String title, boolean published, boolean promoted, boolean sticky) {
        Map<String, Object> values = new LinkedHashMap<>(nodes.create(types.find(ARTICLE).orElseThrow(), 7L).fields());
        values.put(BaseFieldDefinition.STATUS, published);
        values.put(NodeEntityType.PROMOTE, promoted);
        values.put(NodeEntityType.STICKY, sticky);
        return nodes.save(EntityData.of(NodeEntityType.ID, null, ARTICLE, title, values), 7L);
    }

    private Map<String, Object> fieldsAfter(String actionId, EntityData node) {
        actions.execute(actionId, node, articleEditor());
        return nodes.find(((Number) node.id()).longValue()).orElseThrow().fields();
    }

    @Test
    void everySimpleActionIsOfferedByLabelAndAConfigurableOneOnlyOnceStored() {
        assertThat(actions.actions()).extracting(ActionConfig::id).containsExactly(
                BlockUser.ID, DeleteNode.ID, MakeNodeSticky.ID, MakeNodeUnsticky.ID, PromoteNode.ID,
                PublishNode.ID, UnpromoteNode.ID, SaveNode.ID, UnblockUser.ID, UnpublishNode.ID);
        assertThat(actions.actionsFor(UserEntityType.ID)).extracting(ActionConfig::id)
                .containsExactly(BlockUser.ID, UnblockUser.ID);
    }

    @Test
    void eachFieldActionSetsItsFieldAndSavesTheNode() {
        EntityData node = article("Spring schedule", false, false, false);

        assertThat(fieldsAfter(PublishNode.ID, node)).containsEntry(BaseFieldDefinition.STATUS, true);
        assertThat(fieldsAfter(PromoteNode.ID, node)).containsEntry(NodeEntityType.PROMOTE, true);
        assertThat(fieldsAfter(MakeNodeSticky.ID, node)).containsEntry(NodeEntityType.STICKY, true);
        assertThat(fieldsAfter(UnpublishNode.ID, node)).containsEntry(BaseFieldDefinition.STATUS, false);
        assertThat(fieldsAfter(UnpromoteNode.ID, node)).containsEntry(NodeEntityType.PROMOTE, false);
        assertThat(fieldsAfter(MakeNodeUnsticky.ID, node)).containsEntry(NodeEntityType.STICKY, false);
    }

    @Test
    void applyingAnActionIsLogged() {
        EntityData node = article("Spring schedule", false, false, false);

        actions.execute(PublishNode.ID, node, articleEditor());

        LogEntry entry = log.page(LogFilter.ALL, 0, 10).getFirst();
        assertThat(entry.type()).isEqualTo(ActionManager.LOG_CHANNEL);
        assertThat(entry.severity()).isEqualTo(Severity.NOTICE);
        assertThat(entry.formatted())
                .isEqualTo("Publish content was applied to node Spring schedule (" + node.id() + ").");
    }

    @Test
    void savingANodeKeepsItsValuesAndStampsANewRevision() {
        EntityData node = article("Spring schedule", true, false, false);

        actions.execute(SaveNode.ID, node, articleEditor());

        EntityData saved = nodes.find(((Number) node.id()).longValue()).orElseThrow();
        assertThat(saved.revisionId()).isNotEqualTo(node.revisionId());
        assertThat(saved.fields()).containsEntry(NodeEntityType.REVISION_USER, 5L);
    }

    @Test
    void aSaveBySomeoneWithoutAnAccountIsStampedAsAnonymous() {
        EntityData node = article("Spring schedule", true, false, false);

        actions.execute(SaveNode.ID, node, UsernamePasswordAuthenticationToken.authenticated("service", null,
                AuthorityUtils.createAuthorityList(NodePermissions.editAny(ARTICLE))));

        assertThat(nodes.find(((Number) node.id()).longValue()).orElseThrow().fields())
                .containsEntry(NodeEntityType.REVISION_USER, 0L);
    }

    @Test
    void deletingANodeRemovesItAndAsksForConfirmation() {
        EntityData node = article("Spring schedule", true, false, false);

        actions.execute(DeleteNode.ID, node, articleEditor());

        assertThat(nodes.find(((Number) node.id()).longValue())).isEmpty();
        assertThat(actions.plugin(actions.find(DeleteNode.ID).orElseThrow()).destructive()).isTrue();
        assertThat(actions.plugin(actions.find(PublishNode.ID).orElseThrow()).destructive()).isFalse();
    }

    @Test
    void someoneWhoMayNotChangeTheNodeIsRefusedAndNothingChanges() {
        EntityData node = article("Spring schedule", false, false, false);

        assertThatThrownBy(() -> actions.execute(PublishNode.ID, node, account(6L)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("You may not apply Publish content to Spring schedule.");
        assertThatThrownBy(() -> actions.execute(DeleteNode.ID, node,
                account(6L, NodePermissions.editAny(ARTICLE))))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(nodes.find(((Number) node.id()).longValue()).orElseThrow().fields())
                .containsEntry(BaseFieldDefinition.STATUS, false);
        assertThat(log.count(LogFilter.ALL)).isZero();
    }

    @Test
    void publishingIsRefusedForContentUnderAWorkflowButPromotingIsNot() {
        moderations.installEditorial();
        moderations.saveConfig(moderations.config(ContentModerationService.EDITORIAL).orElseThrow()
                .withBundles(List.of(ARTICLE)));
        EntityData node = article("Spring schedule", false, false, false);

        assertThat(actions.plugin(actions.find(PublishNode.ID).orElseThrow()).access(node, articleEditor())
                .allowed()).isFalse();
        assertThat(fieldsAfter(PromoteNode.ID, node)).containsEntry(NodeEntityType.PROMOTE, true);
    }

    @Test
    void anActionForAnotherTypeOfEntityOrAnUnknownActionIsRefused() {
        EntityData node = article("Spring schedule", false, false, false);

        assertThatThrownBy(() -> actions.execute(BlockUser.ID, node, articleEditor()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Block account does not apply to node");
        assertThatThrownBy(() -> actions.execute("node_archive", node, articleEditor()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blockingAndUnblockingAnAccountNeedsThePermissionAndNeverAppliesToAnonymous() {
        UserAccount ada = accounts.create("ada", "ada@example.com", "");
        EntityData adaEntity = accounts.load(ada.id()).orElseThrow();
        Authentication administrator = account(5L, Permissions.ADMINISTER_USERS);

        actions.execute(BlockUser.ID, adaEntity, administrator);
        assertThat(accounts.find(ada.id()).orElseThrow().active()).isFalse();
        actions.execute(UnblockUser.ID, adaEntity, administrator);
        assertThat(accounts.find(ada.id()).orElseThrow().active()).isTrue();

        assertThatThrownBy(() -> actions.execute(BlockUser.ID, adaEntity, account(6L)))
                .isInstanceOf(AccessDeniedException.class);
        EntityData anonymous = accounts.load(UserAccount.ANONYMOUS_ID).orElseThrow();
        assertThatThrownBy(() -> actions.execute(UnblockUser.ID, anonymous, administrator))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void theFirstAccountMayBlockWithoutThePermission() {
        UserAccount ada = accounts.create("ada", "ada@example.com", "");
        AccountPrincipal first = new AccountPrincipal(UserAccount.ADMINISTRATOR_ID, "admin", "", true, List.of());

        actions.execute(BlockUser.ID, accounts.load(ada.id()).orElseThrow(),
                UsernamePasswordAuthenticationToken.authenticated(first, null, first.getAuthorities()));

        assertThat(accounts.find(ada.id()).orElseThrow().active()).isFalse();
    }

    @Test
    void aConfigurableActionIsOfferedOnceStoredWithValidSettings() {
        UserAccount grace = accounts.create("grace", "grace@example.com", "");

        assertThat(actions.save(new ActionConfig("assign_to_grace", "Make Grace the author", AssignNodeOwner.ID,
                Map.of(AssignNodeOwner.OWNER, "nobody")))).containsEntry(AssignNodeOwner.OWNER,
                "There is no account named nobody.");
        assertThat(actions.save(new ActionConfig("assign_to_grace", "Make Grace the author", AssignNodeOwner.ID,
                Map.of()))).containsEntry(AssignNodeOwner.OWNER, "This value is required.");
        assertThat(actions.save(new ActionConfig("archive", "Archive", "node_archive", Map.of())))
                .containsEntry("plugin", "Choose an action.");
        assertThat(actions.find("assign_to_grace")).isEmpty();

        assertThat(actions.save(new ActionConfig("assign_to_grace", "Make Grace the author", AssignNodeOwner.ID,
                Map.of(AssignNodeOwner.OWNER, "grace")))).isEmpty();
        EntityData node = article("Spring schedule", true, false, false);
        actions.execute("assign_to_grace", node, articleEditor());

        assertThat(nodes.find(((Number) node.id()).longValue()).orElseThrow().fields())
                .containsEntry(BaseFieldDefinition.OWNER, grace.id());
        assertThat(log.page(LogFilter.ALL, 0, 1).getFirst().formatted()).startsWith("Make Grace the author was");
    }

    @Test
    void aConfigurableActionWhoseAccountWasRemovedFailsWithoutChangingTheNode() {
        UserAccount grace = accounts.create("grace", "grace@example.com", "");
        actions.save(new ActionConfig("assign_to_grace", "Make Grace the author", AssignNodeOwner.ID,
                Map.of(AssignNodeOwner.OWNER, "grace")));
        entities.delete(UserEntityType.ID, grace.id());
        EntityData node = article("Spring schedule", true, false, false);

        assertThatThrownBy(() -> actions.execute("assign_to_grace", node, articleEditor()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("There is no account named grace.");
    }

    @Test
    void theAuthorActionsFormAsksForAUsername() {
        Action assign = actions.plugin(new ActionConfig("x", "x", AssignNodeOwner.ID, Map.of()));

        assertThat(assign.configurationForm(Map.of(AssignNodeOwner.OWNER, "grace"))).singleElement()
                .satisfies(element -> {
                    assertThat(element.name()).isEqualTo(AssignNodeOwner.OWNER);
                    assertThat(element.value()).isEqualTo("grace");
                    assertThat(element.required()).isTrue();
                });
        assertThat(actions.plugin(actions.find(SaveNode.ID).orElseThrow()).configurationForm(Map.of())).isEmpty();
        assertThat(actions.plugin(actions.find(SaveNode.ID).orElseThrow()).validate(Map.of())).isEmpty();
    }

    @Test
    void aStoredActionWhosePluginIsGoneIsNotOffered() {
        config.save(ActionConfig.configName("archive"), new ActionConfig("archive", "Archive", "node_archive",
                Map.of()));

        assertThat(actions.find("archive")).isEmpty();
    }

    @Test
    void aStoredActionCanBeDeleted() {
        accounts.create("grace", "grace@example.com", "");
        actions.save(new ActionConfig("assign_to_grace", "Make Grace the author", AssignNodeOwner.ID,
                Map.of(AssignNodeOwner.OWNER, "grace")));

        actions.delete("assign_to_grace");

        assertThat(actions.find("assign_to_grace")).isEqualTo(Optional.empty());
    }
}
