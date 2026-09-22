package dev.springdrop.kernel.menu;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.user.AccountPrincipal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class MenuPermissionCheckerTest {

    private static final String READ_THE_LEDGER = "read the ledger";

    private final MenuPermissionChecker permissions = new MenuPermissionChecker();

    @AfterEach
    void nobodyIsSignedIn() {
        SecurityContextHolder.clearContext();
    }

    private static void signedInWith(String... held) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("reader", "secret",
                        List.of(held).stream().map(SimpleGrantedAuthority::new).toList()));
    }

    @Test
    void someoneWhoHasNotSignedInHoldsNothing() {
        assertThat(permissions.holds(READ_THE_LEDGER)).isFalse();
    }

    @Test
    void someoneSignedInWithoutThePermissionDoesNotHoldIt() {
        signedInWith("read the newsletter");

        assertThat(permissions.holds(READ_THE_LEDGER)).isFalse();
    }

    @Test
    void someoneSignedInWithThePermissionHoldsIt() {
        signedInWith(READ_THE_LEDGER);

        assertThat(permissions.holds(READ_THE_LEDGER)).isTrue();
    }

    @Test
    void theFirstAccountHoldsEverythingWithoutBeingGrantedIt() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AccountPrincipal(1L, "root", "", true, List.of()), "secret", List.of()));

        assertThat(permissions.holds(READ_THE_LEDGER)).isTrue();
    }
}
