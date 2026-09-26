package dev.springdrop.kernel.user;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.role.RoleConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class AccountPrincipalsTest {

    private static UsernamePasswordAuthenticationToken signedInAs(long id) {
        AccountPrincipal principal = new AccountPrincipal(id, "somebody", "", true, List.of());
        return new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities());
    }

    @Test
    void theFirstAccountIsNotSubjectToChecks() {
        assertThat(AccountPrincipals.bypassesChecks(signedInAs(UserAccount.ADMINISTRATOR_ID))).isTrue();
    }

    @Test
    void everyOtherAccountIs() {
        assertThat(AccountPrincipals.bypassesChecks(signedInAs(7L))).isFalse();
    }

    @Test
    void soIsSomethingThatIsNotAnAccountAtAll() {
        assertThat(AccountPrincipals.bypassesChecks(
                new UsernamePasswordAuthenticationToken("plain", "", List.of()))).isFalse();
    }

    @Test
    void andSoIsNobodyAtAll() {
        assertThat(AccountPrincipals.bypassesChecks(null)).isFalse();
    }

    @Test
    void nobodyAtAllHoldsTheAnonymousRoleAlone() {
        assertThat(AccountPrincipals.rolesOf(null)).containsExactly(RoleConfig.ANONYMOUS);
    }

    @Test
    void aVisitorSpringSecurityMarksAsAnonymousHoldsTheAnonymousRoleAlone() {
        AnonymousAuthenticationToken visitor = new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        assertThat(AccountPrincipals.rolesOf(visitor)).containsExactly(RoleConfig.ANONYMOUS);
    }

    @Test
    void anAccountHoldsTheAuthenticatedRoleAndItsOwnButNotItsPermissions() {
        AccountPrincipal principal = new AccountPrincipal(
                7L, "editor", "", true, List.of("administer menu"), List.of("editor"));

        assertThat(AccountPrincipals.rolesOf(
                new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities())))
                .containsExactly(RoleConfig.AUTHENTICATED, "editor");
    }
}
