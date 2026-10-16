package dev.springdrop.kernel.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import dev.springdrop.kernel.security.Permissions;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminToolbarTest {

    private final AdminToolbar toolbar = new AdminToolbar(mock(MenuTreeBuilder.class));

    @AfterEach
    void forgetTheRequester() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aRequestNoOneHasIdentifiedGetsNoToolbarAndNoName() {
        SecurityContextHolder.clearContext();

        AdminToolbar.View view = toolbar.view("/");

        assertThat(view.shown()).isFalse();
        assertThat(view.accountName()).isEmpty();
    }

    @Test
    void aVisitorWhoseRoleGrantsTheToolbarSeesItWithoutAnAccountName() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("visitor",
                "anonymousUser", List.of(new SimpleGrantedAuthority(Permissions.ACCESS_TOOLBAR))));

        AdminToolbar.View view = toolbar.view("/");

        assertThat(view.shown()).isTrue();
        assertThat(view.accountName()).isEmpty();
    }
}
