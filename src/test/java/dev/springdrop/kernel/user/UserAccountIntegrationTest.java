package dev.springdrop.kernel.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.logout;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;

import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.routing.RouteDefinition;
import dev.springdrop.kernel.routing.RouteRegistrar;
import dev.springdrop.kernel.security.SecurityConfig;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@SpringBootTest
@AutoConfigureMockMvc
class UserAccountIntegrationTest extends AbstractIntegrationTest {

    private static final String SECRET = "correct horse battery staple";

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private AccountDetailsService accountDetails;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void accountStorageAndTheTwoReservedAccounts() {
        accounts.install();
        List.of("alice", "bob", "blocked").forEach(name ->
                accounts.findByName(name).ifPresent(account -> entities.delete("user", account.id())));
    }

    private UserAccount createAlice() {
        return accounts.create("alice", "alice@example.com", passwordEncoder.encode(SECRET));
    }

    @Test
    void theSiteAlwaysHasAnAnonymousAndAnAdministratorAccount() {
        assertThat(accounts.find(UserAccount.ANONYMOUS_ID)).hasValueSatisfying(anonymous -> {
            assertThat(anonymous.name()).isEqualTo(UserAccount.ANONYMOUS_NAME);
            assertThat(anonymous.isAnonymous()).isTrue();
            assertThat(anonymous.active()).isFalse();
        });
        assertThat(accounts.find(UserAccount.ADMINISTRATOR_ID)).hasValueSatisfying(administrator -> {
            assertThat(administrator.name()).isEqualTo("admin");
            assertThat(administrator.bypassesAccessChecks()).isTrue();
        });
    }

    @Test
    void installingTwiceLeavesTheReservedAccountsAsTheyWere() {
        accounts.install();

        assertThat(accounts.find(UserAccount.ADMINISTRATOR_ID))
                .hasValueSatisfying(administrator -> assertThat(administrator.name()).isEqualTo("admin"));
    }

    @Test
    void anAccountSavesAndLoadsWithACustomFieldOfItsOwn() {
        fields.createStorage(FieldStorageConfig.single("nickname", "user", StringFieldType.ID));
        fields.createInstance(FieldInstanceConfig.of("nickname", "user", "user", "Nickname"));

        UserAccount alice = accounts.create("alice", "alice@example.com",
                passwordEncoder.encode(SECRET), Map.of("nickname", "Ali"));

        assertThat(accounts.load(alice.id()))
                .hasValueSatisfying(stored -> assertThat(stored.fields()).containsEntry("nickname", "Ali"));

        fields.deleteStorage("user", "nickname");
    }

    @Test
    void anAccountIsFoundByItsName() {
        createAlice();

        assertThat(accounts.findByName("alice")).hasValueSatisfying(alice -> {
            assertThat(alice.mail()).isEqualTo("alice@example.com");
            assertThat(alice.active()).isTrue();
        });
        assertThat(accounts.findByName("nobody")).isEmpty();
        assertThat(accounts.find(9999L)).isEmpty();
    }

    @Test
    void twoAccountsCannotShareAnAddressWhateverCaseItWasTypedIn() {
        createAlice();

        assertThatThrownBy(() -> accounts.create("bob", "ALICE@example.com", "hash"))
                .isInstanceOf(DuplicateAccountException.class)
                .hasMessageContaining("ALICE@example.com");
    }

    @Test
    void twoAccountsCannotShareANameWhateverCaseItWasTypedIn() {
        createAlice();

        assertThatThrownBy(() -> accounts.create("Alice", "other@example.com", "hash"))
                .isInstanceOf(DuplicateAccountException.class);
    }

    @Test
    void theFirstAccountBypassesAPermissionEveryoneElseIsDeniedBy() throws Exception {
        mockMvc.perform(get("/needs-permission")
                        .with(authentication(principalFor(UserAccount.ADMINISTRATOR_ID, "admin"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/needs-permission")
                        .with(authentication(principalFor(7L, "alice"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aPrincipalCarriesTheAccountItStandsFor() {
        AccountPrincipal principal = new AccountPrincipal(7L, "alice", "hash", true, List.of("edit"));

        assertThat(principal.id()).isEqualTo(7L);
        assertThat(principal.getUsername()).isEqualTo("alice");
        assertThat(principal.getPassword()).isEqualTo("hash");
        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.bypassesAccessChecks()).isFalse();
        assertThat(principal.getAuthorities()).extracting(value -> value.toString()).containsExactly("edit");
    }

    @Test
    void onlyTheReservedAccountsAreAnonymousOrUnchecked() {
        UserAccount ordinary = new UserAccount(7L, "alice", "alice@example.com", true, "UTC", "en");

        assertThat(ordinary.isAnonymous()).isFalse();
        assertThat(ordinary.bypassesAccessChecks()).isFalse();
    }

    @Test
    void aSignedInAccountThatIsNotTheFirstStillNeedsThePermission() throws Exception {
        createAlice();

        mockMvc.perform(get("/needs-permission")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                "alice", "", List.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void aSignInPageIsShownForAnAccountThatCannotBeLoaded() {
        assertThatThrownBy(() -> accountDetails.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void theSignInPageAsksForANameAndAPasswordThatIsNotShown() throws Exception {
        org.jsoup.nodes.Document document = org.jsoup.Jsoup.parse(loginPage(""));

        assertThat(document.selectFirst("input[name=username]").attr("type")).isEqualTo("text");
        assertThat(document.selectFirst("input[name=password]").attr("type")).isEqualTo("password");
        assertThat(document.selectFirst("input[name=remember-me]").attr("type")).isEqualTo("checkbox");
    }

    @Test
    void theSignInPageSaysWhenCredentialsDidNotMatchAndWhenSomeoneSignedOut() throws Exception {
        assertThat(loginPage("?error=1")).contains("do not match an account");
        assertThat(loginPage("?logout=1")).contains("signed out");
        assertThat(loginPage("")).doesNotContain("signed out");
    }

    @Test
    void theRightNameAndPasswordSignSomeoneIn() throws Exception {
        createAlice();

        mockMvc.perform(formLogin(SecurityConfig.LOGIN_PATH).user("alice").password(SECRET))
                .andExpect(authenticated().withUsername("alice"))
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void aWrongPasswordSignsNobodyIn() throws Exception {
        createAlice();

        mockMvc.perform(formLogin(SecurityConfig.LOGIN_PATH).user("alice").password("guess"))
                .andExpect(unauthenticated());
    }

    @Test
    void aBlockedAccountIsRefusedEvenWithTheRightPassword() throws Exception {
        UserAccount blocked = accounts.create("blocked", "blocked@example.com",
                passwordEncoder.encode(SECRET));
        accounts.block(blocked.id());

        assertThat(accountDetails.loadUserByUsername("blocked").isEnabled()).isFalse();
        mockMvc.perform(formLogin(SecurityConfig.LOGIN_PATH).user("blocked").password(SECRET))
                .andExpect(unauthenticated());
    }

    @Test
    void anAccountThatIsNotThereCannotBeLoadedForSignIn() {
        assertThatThrownBy(() -> accountDetails.loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("nobody");
    }

    @Test
    void askingToStaySignedInLeavesACookieBehind() throws Exception {
        createAlice();

        mockMvc.perform(post(SecurityConfig.LOGIN_PATH)
                        .param("username", "alice")
                        .param("password", SECRET)
                        .param("remember-me", "on")
                        .with(csrf()))
                .andExpect(authenticated())
                .andExpect(cookie().exists("remember-me"));
    }

    @Test
    void signingOutEndsTheSession() throws Exception {
        createAlice();

        mockMvc.perform(logout(SecurityConfig.LOGOUT_PATH))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/"));
    }

    private String loginPage(String query) throws Exception {
        return mockMvc.perform(get(SecurityConfig.LOGIN_PATH + query))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static UsernamePasswordAuthenticationToken principalFor(long id, String name) {
        AccountPrincipal principal = new AccountPrincipal(id, name, "", true, List.of());
        return new UsernamePasswordAuthenticationToken(principal, "", principal.getAuthorities());
    }

    @TestConfiguration
    static class SecureRoute {

        @Bean
        RouteRegistrar accountTestRoutes() {
            return () -> List.of(RouteDefinition.admin(
                    "/needs-permission", "needs_permission", "Restricted", "manage everything"));
        }

        @Controller
        static class RestrictedController {

            @GetMapping("/needs-permission")
            @ResponseBody
            String restricted() {
                return "restricted";
            }
        }
    }
}
