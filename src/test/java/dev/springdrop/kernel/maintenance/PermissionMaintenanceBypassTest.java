package dev.springdrop.kernel.maintenance;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

class PermissionMaintenanceBypassTest {

    @AfterEach
    void forgetTheRequester() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aRequestNoOneHasIdentifiedIsNotLetThrough() {
        SecurityContextHolder.clearContext();

        assertThat(new PermissionMaintenanceBypass().isAllowed(new MockHttpServletRequest())).isFalse();
    }
}
