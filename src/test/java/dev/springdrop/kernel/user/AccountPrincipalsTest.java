package dev.springdrop.kernel.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

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
}
