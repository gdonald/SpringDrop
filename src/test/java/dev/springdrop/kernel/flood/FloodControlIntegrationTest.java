package dev.springdrop.kernel.flood;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;

import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.security.SecurityConfig;
import dev.springdrop.kernel.user.AccountDetailsService;
import dev.springdrop.kernel.user.AccountFloodedException;
import dev.springdrop.kernel.user.LoginFloodGuard;
import dev.springdrop.kernel.user.PasswordResetService;
import dev.springdrop.kernel.user.UserAccountService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import dev.springdrop.support.AbstractIntegrationTest;

@SpringBootTest
@AutoConfigureMockMvc
class FloodControlIntegrationTest extends AbstractIntegrationTest {

    private static final String SECRET = "correct horse battery staple";

    private static final String EVENT = "test.event";

    @Autowired
    private FloodService flood;

    @Autowired
    private LoginFloodGuard loginGuard;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private AccountDetailsService accountDetails;

    @Autowired
    private PasswordResetService resets;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void anAccountAndAClearRecord() {
        accounts.install();
        accounts.findByName("flooded").ifPresent(account -> entities.delete("user", account.id()));
        accounts.create("flooded", "flooded@example.com", passwordEncoder.encode(SECRET));

        List.of("flooded", "nobody").forEach(name -> {
            flood.clear(FloodSettings.LOGIN_USER, name);
            flood.clear(FloodSettings.PASSWORD_RESET, name);
        });
        flood.clear(FloodSettings.LOGIN_IP, "127.0.0.1");
        flood.clear(EVENT, "someone");
    }

    @Test
    void anEventIsAllowedUntilItHasHappenedTooOften() {
        Duration window = Duration.ofMinutes(5);

        assertThat(flood.isAllowed(EVENT, "someone", 2, window)).isTrue();
        flood.register(EVENT, "someone", window);
        assertThat(flood.isAllowed(EVENT, "someone", 2, window)).isTrue();
        flood.register(EVENT, "someone", window);

        assertThat(flood.isAllowed(EVENT, "someone", 2, window)).isFalse();
        assertThat(flood.attempts(EVENT, "someone", window)).isEqualTo(2);
    }

    @Test
    void oneIdentifierCountsSeparatelyFromAnother() {
        Duration window = Duration.ofMinutes(5);
        flood.register(EVENT, "someone", window);
        flood.register(EVENT, "someone", window);

        assertThat(flood.isAllowed(EVENT, "someone else", 2, window)).isTrue();
    }

    @Test
    void attemptsOlderThanTheWindowNoLongerCount() {
        flood.register(EVENT, "someone", Duration.ofMinutes(5));

        assertThat(flood.attempts(EVENT, "someone", Duration.ZERO)).isZero();
        assertThat(flood.isAllowed(EVENT, "someone", 1, Duration.ZERO)).isTrue();
    }

    @Test
    void clearingForgetsWhatWasCountedAgainstOneIdentifier() {
        flood.register(EVENT, "someone", Duration.ofMinutes(5));

        flood.clear(EVENT, "someone");

        assertThat(flood.attempts(EVENT, "someone", Duration.ofMinutes(5))).isZero();
    }

    @Test
    void anIdentifierNothingWasCountedAgainstIsAllowed() {
        assertThat(flood.isAllowed(EVENT, "untouched", 1, Duration.ofMinutes(5))).isTrue();
    }

    @Test
    void failedSignInsPastTheThresholdAreTurnedAwayForTheWindow() throws Exception {
        for (int attempt = 0; attempt < FloodSettings.USER_THRESHOLD; attempt++) {
            mockMvc.perform(formLogin(SecurityConfig.LOGIN_PATH).user("flooded").password("guess"))
                    .andExpect(unauthenticated());
        }

        assertThat(loginGuard.mayAttempt("flooded", "127.0.0.1")).isFalse();
        assertThatThrownBy(() -> accountDetails.loadUserByUsername("flooded"))
                .isInstanceOf(AccountFloodedException.class)
                .hasMessageContaining("Too many failed sign-ins");
        mockMvc.perform(formLogin(SecurityConfig.LOGIN_PATH).user("flooded").password(SECRET))
                .andExpect(unauthenticated());
    }

    @Test
    void signingInSuccessfullyForgetsTheFailuresBeforeIt() throws Exception {
        mockMvc.perform(formLogin(SecurityConfig.LOGIN_PATH).user("flooded").password("guess"))
                .andExpect(unauthenticated());
        assertThat(flood.attempts(FloodSettings.LOGIN_USER, "flooded", FloodSettings.USER_WINDOW))
                .isEqualTo(1);

        mockMvc.perform(formLogin(SecurityConfig.LOGIN_PATH).user("flooded").password(SECRET))
                .andExpect(authenticated());

        assertThat(flood.attempts(FloodSettings.LOGIN_USER, "flooded", FloodSettings.USER_WINDOW))
                .isZero();
        assertThat(loginGuard.mayAttempt("flooded", "127.0.0.1")).isTrue();
    }

    @Test
    void anAddressThatHasNotTriedTooOftenIsStillAllowed() {
        loginGuard.recordFailure("flooded", "10.0.0.1");

        assertThat(loginGuard.mayAttempt("someone-else", "10.0.0.1")).isTrue();
        flood.clear(FloodSettings.LOGIN_IP, "10.0.0.1");
    }

    @Test
    void askingForTooManyResetLinksStopsSendingThem() {
        for (int attempt = 0; attempt < FloodSettings.RESET_THRESHOLD; attempt++) {
            assertThat(resets.requestReset("flooded")).isPresent();
        }

        assertThat(resets.requestReset("flooded")).isEmpty();
    }

    @Test
    void anAttemptWithNoAddressRecordedStillCounts() {
        var attempt = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "flooded", "guess");
        loginGuard.onFailure(
                new org.springframework.security.authentication.event
                        .AuthenticationFailureBadCredentialsEvent(attempt,
                        new org.springframework.security.authentication.BadCredentialsException("no")));

        assertThat(flood.attempts(FloodSettings.LOGIN_USER, "flooded", FloodSettings.USER_WINDOW))
                .isEqualTo(1);
        flood.clear(FloodSettings.LOGIN_IP, "unknown");
    }

    @Test
    void aRecordOfAttemptsKeepsWhatItWasGiven() {
        FloodService.Attempts attempts = new FloodService.Attempts(List.of(1L, 2L));

        assertThat(attempts.seconds()).containsExactly(1L, 2L);
    }
}
