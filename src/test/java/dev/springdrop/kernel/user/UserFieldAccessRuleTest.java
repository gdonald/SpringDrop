package dev.springdrop.kernel.user;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.FieldAccessRule;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.security.Permissions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

class UserFieldAccessRuleTest {

    private static final EntityType USER = UserEntityType.definition();

    private static final EntityData ACCOUNT = EntityData.of(UserEntityType.ID, 7L, null, "ada", Map.of());

    private final UserFieldAccessRule rule = new UserFieldAccessRule();

    private static Authentication account(long id, String... permissions) {
        AccountPrincipal principal = new AccountPrincipal(id, "account" + id, "", true, List.of(permissions));
        return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
    }

    private boolean forbidden(String field, String operation, EntityData entity, Authentication who) {
        return rule.check(USER, field, operation, entity, who).forbidden();
    }

    @Test
    void thePasswordHashIsClosedEvenToTheFirstAccount() {
        assertThat(forbidden(UserEntityType.PASSWORD_HASH, FieldAccessRule.VIEW, ACCOUNT,
                account(UserAccount.ADMINISTRATOR_ID))).isTrue();
        assertThat(forbidden(UserEntityType.PASSWORD_HASH, FieldAccessRule.EDIT, ACCOUNT, account(7L))).isTrue();
    }

    @Test
    void theEmailAddressIsOpenToTheAccountItselfAndToUserAdministrators() {
        assertThat(forbidden(UserEntityType.MAIL, FieldAccessRule.VIEW, ACCOUNT, account(7L))).isFalse();
        assertThat(forbidden(UserEntityType.MAIL, FieldAccessRule.EDIT, ACCOUNT,
                account(8L, Permissions.ADMINISTER_USERS))).isFalse();
        assertThat(forbidden(UserEntityType.MAIL, FieldAccessRule.VIEW, ACCOUNT, account(8L))).isTrue();
    }

    @Test
    void theEmailAddressOfAnAccountNotYetSavedIsOpenOnlyToUserAdministrators() {
        EntityData unsaved = EntityData.of(UserEntityType.ID, null, null, "", Map.of());

        assertThat(forbidden(UserEntityType.MAIL, FieldAccessRule.EDIT, unsaved, account(7L))).isTrue();
    }

    @Test
    void someoneSignedOutOrWithoutAnAccountSeesNoEmailAddress() {
        assertThat(forbidden(UserEntityType.MAIL, FieldAccessRule.VIEW, ACCOUNT, null)).isTrue();
        assertThat(forbidden(UserEntityType.MAIL, FieldAccessRule.VIEW, ACCOUNT,
                new TestingAuthenticationToken("visitor", null, List.of()))).isTrue();
    }

    @Test
    void onlyUserAdministratorsEditTheStatusAndRoles() {
        assertThat(forbidden(BaseFieldDefinition.STATUS, FieldAccessRule.EDIT, ACCOUNT, account(7L))).isTrue();
        assertThat(forbidden(UserEntityType.ROLES, FieldAccessRule.EDIT, ACCOUNT,
                account(8L, Permissions.ADMINISTER_USERS))).isFalse();
        assertThat(forbidden(UserEntityType.ROLES, FieldAccessRule.VIEW, ACCOUNT, account(7L))).isFalse();
    }

    @Test
    void otherFieldsAndOtherEntityTypesAreLeftToOtherRules() {
        assertThat(forbidden(UserEntityType.TIMEZONE, FieldAccessRule.EDIT, ACCOUNT, account(7L))).isFalse();
        assertThat(rule.check(NodeEntityType.definition(), BaseFieldDefinition.STATUS, FieldAccessRule.EDIT,
                ACCOUNT, account(7L)).forbidden()).isFalse();
    }
}
