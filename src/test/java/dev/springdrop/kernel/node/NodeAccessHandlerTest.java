package dev.springdrop.kernel.node;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.access.AccessResult;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.user.AccountPrincipal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class NodeAccessHandlerTest {

    private static final EntityType NODE = NodeEntityType.definition();

    private static final String ARTICLE = "article";

    private static final long AUTHOR = 7L;

    private static final long SOMEONE_ELSE = 8L;

    private final NodeAccessHandler handler = new NodeAccessHandler();

    private static Authentication account(long id, String... permissions) {
        AccountPrincipal principal = new AccountPrincipal(id, "account" + id, "", true, List.of(permissions));
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    private static EntityData article(boolean published) {
        return new EntityData(NodeEntityType.ID, 3L, null, ARTICLE, "Spring schedule", EntityData.DEFAULT_LANGCODE,
                null, Map.of(BaseFieldDefinition.STATUS, published, BaseFieldDefinition.OWNER, AUTHOR));
    }

    private AccessResult check(Object entity, String operation, Authentication authentication) {
        return handler.check(NODE, entity, operation, authentication);
    }

    @Nested
    class Viewing {

        @Test
        void aPublishedNodeIsOpenToAnyoneWhoMayAccessContent() {
            assertThat(check(article(true), EntityAccessHandler.VIEW,
                    account(SOMEONE_ELSE, NodePermissions.ACCESS_CONTENT)).allowed()).isTrue();
        }

        @Test
        void aPublishedNodeIsClosedToSomeoneWhoMayNotAccessContent() {
            assertThat(check(article(true), EntityAccessHandler.VIEW, account(SOMEONE_ELSE)).allowed()).isFalse();
        }

        @Test
        void anUnpublishedNodeIsOpenToItsOwnerWhoMayViewTheirOwnUnpublishedContent() {
            assertThat(check(article(false), EntityAccessHandler.VIEW,
                    account(AUTHOR, NodePermissions.VIEW_OWN_UNPUBLISHED)).allowed()).isTrue();
        }

        @Test
        void anUnpublishedNodeIsClosedToSomeoneElseWhoMayViewTheirOwnUnpublishedContent() {
            assertThat(check(article(false), EntityAccessHandler.VIEW,
                    account(SOMEONE_ELSE, NodePermissions.VIEW_OWN_UNPUBLISHED, NodePermissions.ACCESS_CONTENT))
                    .allowed()).isFalse();
        }

        @Test
        void anUnpublishedNodeIsOpenToSomeoneWhoMayViewAnyUnpublishedContent() {
            assertThat(check(article(false), EntityAccessHandler.VIEW,
                    account(SOMEONE_ELSE, NodePermissions.VIEW_ANY_UNPUBLISHED)).allowed()).isTrue();
        }

        @Test
        void anUnpublishedNodeIsClosedToItsOwnerWithoutThePermission() {
            assertThat(check(article(false), EntityAccessHandler.VIEW, account(AUTHOR)).allowed()).isFalse();
        }

        @Test
        void anAnswerAboutAnUnpublishedNodeVariesByWhoIsAsking() {
            assertThat(check(article(false), EntityAccessHandler.VIEW, account(AUTHOR)).cacheContexts())
                    .contains(NodeAccessHandler.USER_CONTEXT);
        }

        @Test
        void anAnswerAboutANodeIsInvalidatedWhenTheNodeChanges() {
            assertThat(check(article(true), EntityAccessHandler.VIEW, account(AUTHOR)).cacheTags())
                    .containsExactly(NodeService.cacheTag(3L));
        }

        @Test
        void someoneNotSignedInIsAskedAboutByTheirPermissionsAlone() {
            assertThat(check(article(false), EntityAccessHandler.VIEW, null).allowed()).isFalse();
        }
    }

    @Nested
    class Editing {

        @Test
        void editingAnyNodeOfATypeIsAllowedByEditAny() {
            assertThat(check(article(true), EntityAccessHandler.UPDATE,
                    account(SOMEONE_ELSE, NodePermissions.editAny(ARTICLE))).allowed()).isTrue();
        }

        @Test
        void editingOnesOwnNodeIsAllowedByEditOwn() {
            assertThat(check(article(true), EntityAccessHandler.UPDATE,
                    account(AUTHOR, NodePermissions.editOwn(ARTICLE))).allowed()).isTrue();
        }

        @Test
        void editingSomeoneElsesNodeIsNotAllowedByEditOwn() {
            assertThat(check(article(true), EntityAccessHandler.UPDATE,
                    account(SOMEONE_ELSE, NodePermissions.editOwn(ARTICLE))).allowed()).isFalse();
        }

        @Test
        void editingAnotherTypesNodesIsNotAllowedByThisTypesPermission() {
            assertThat(check(article(true), EntityAccessHandler.UPDATE,
                    account(AUTHOR, NodePermissions.editAny("page"))).allowed()).isFalse();
        }
    }

    @Nested
    class Deleting {

        @Test
        void deletingAnyNodeOfATypeIsAllowedByDeleteAny() {
            assertThat(check(article(true), EntityAccessHandler.DELETE,
                    account(SOMEONE_ELSE, NodePermissions.deleteAny(ARTICLE))).allowed()).isTrue();
        }

        @Test
        void deletingOnesOwnNodeIsAllowedByDeleteOwn() {
            assertThat(check(article(true), EntityAccessHandler.DELETE,
                    account(AUTHOR, NodePermissions.deleteOwn(ARTICLE))).allowed()).isTrue();
        }

        @Test
        void deletingSomeoneElsesNodeIsNotAllowedByDeleteOwn() {
            assertThat(check(article(true), EntityAccessHandler.DELETE,
                    account(SOMEONE_ELSE, NodePermissions.deleteOwn(ARTICLE))).allowed()).isFalse();
        }
    }

    @Nested
    class Creating {

        @Test
        void creatingContentOfATypeIsAllowedByThatTypesCreatePermission() {
            assertThat(check(ARTICLE, EntityAccessHandler.CREATE,
                    account(AUTHOR, NodePermissions.create(ARTICLE))).allowed()).isTrue();
        }

        @Test
        void creatingContentOfATypeIsNotAllowedByAnotherTypesCreatePermission() {
            assertThat(check(ARTICLE, EntityAccessHandler.CREATE,
                    account(AUTHOR, NodePermissions.create("page"))).allowed()).isFalse();
        }

        @Test
        void creatingIsAskedAboutAContentTypeRatherThanANode() {
            assertThat(check(article(true), EntityAccessHandler.CREATE,
                    account(AUTHOR, NodePermissions.create(ARTICLE))).neutralDecision()).isTrue();
        }
    }

    @Test
    void bypassingNodeAccessAllowsEveryOperation() {
        Authentication bypass = account(SOMEONE_ELSE, NodePermissions.BYPASS_NODE_ACCESS);

        assertThat(List.of(EntityAccessHandler.VIEW, EntityAccessHandler.UPDATE, EntityAccessHandler.DELETE))
                .allSatisfy(operation -> assertThat(check(article(false), operation, bypass).allowed()).isTrue());
    }

    @Test
    void anOperationNodesDoNotHaveSaysNothing() {
        assertThat(check(article(true), "publish", account(AUTHOR, NodePermissions.ACCESS_CONTENT))
                .neutralDecision()).isTrue();
    }

    @Test
    void somethingOtherThanANodeSaysNothing() {
        assertThat(check("not a node", EntityAccessHandler.VIEW, account(AUTHOR, NodePermissions.ACCESS_CONTENT))
                .neutralDecision()).isTrue();
    }

    @Test
    void someoneWhoIsNotASiteAccountOwnsNothing() {
        Authentication outsider = UsernamePasswordAuthenticationToken.authenticated("service", null,
                List.of(new SimpleGrantedAuthority(
                        NodePermissions.editOwn(ARTICLE))));

        assertThat(check(article(true), EntityAccessHandler.UPDATE, outsider).allowed()).isFalse();
    }

    @Test
    void aNodeWithNoOwnerIsNobodysOwn() {
        EntityData unowned = article(true).withFields(Map.of(BaseFieldDefinition.STATUS, true));

        assertThat(check(unowned, EntityAccessHandler.UPDATE, account(AUTHOR, NodePermissions.editOwn(ARTICLE)))
                .allowed()).isFalse();
    }
}
