package dev.springdrop.kernel.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.mail.CollectingMailBackend;
import dev.springdrop.kernel.mail.MailMessage;
import dev.springdrop.kernel.security.SecurityConfig;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.web.AccountController;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class RegistrationIntegrationTest extends AbstractIntegrationTest {

    private static final String SECRET = "correct horse battery staple";

    @Autowired
    private RegistrationService registrations;

    @Autowired
    private PasswordResetService resets;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private OneTimeLinkService oneTimeLinks;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private ConfigStore configStore;

    @Autowired
    private CollectingMailBackend mailbox;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void accountsAndAnEmptyMailbox() {
        accounts.install();
        List.of("newcomer", "waiting", "forgetful").forEach(name ->
                accounts.findByName(name).ifPresent(account -> entities.delete("user", account.id())));
        configStore.save(SiteInformation.CONFIG_NAME, SiteInformation.DEFAULTS);
        mailbox.clear();
    }

    @AfterEach
    void defaultRegistrationSettings() {
        configStore.save(UserSettings.CONFIG_NAME, UserSettings.DEFAULTS);
    }

    private void registrationMode(String mode, boolean verifyMail) {
        configStore.save(UserSettings.CONFIG_NAME, new UserSettings(mode, verifyMail));
    }

    private MailMessage lastMessage() {
        return mailbox.delivered().getLast();
    }

    @Test
    void anOpenSiteLetsSomeoneSignUpAndUseTheAccountAtOnce() {
        registrationMode(UserSettings.OPEN, false);

        UserAccount account = registrations.register("newcomer", "newcomer@example.com", SECRET);

        assertThat(account.active()).isTrue();
        assertThat(lastMessage().subject()).isEqualTo("Your account at SpringDrop");
    }

    @Test
    void aSiteThatApprovesAccountsLeavesANewOneWaiting() throws Exception {
        registrationMode(UserSettings.ADMIN_APPROVAL, false);

        UserAccount account = registrations.register("waiting", "waiting@example.com", SECRET);

        assertThat(account.active()).isFalse();
        assertThat(lastMessage().subject()).contains("waiting");
        mockMvc.perform(formLogin(SecurityConfig.LOGIN_PATH).user("waiting").password(SECRET))
                .andExpect(unauthenticated());
    }

    @Test
    void approvingTheAccountLetsThePersonIn() throws Exception {
        registrationMode(UserSettings.ADMIN_APPROVAL, false);
        UserAccount account = registrations.register("waiting", "waiting@example.com", SECRET);

        registrations.approve(account.id());

        assertThat(accounts.find(account.id()))
                .hasValueSatisfying(approved -> assertThat(approved.active()).isTrue());
        assertThat(lastMessage().subject()).contains("is open");
        mockMvc.perform(formLogin(SecurityConfig.LOGIN_PATH).user("waiting").password(SECRET))
                .andExpect(authenticated().withUsername("waiting"));
    }

    @Test
    void aSiteThatChecksAddressesWaitsForTheLinkToBeUsed() {
        registrationMode(UserSettings.OPEN, true);

        UserAccount account = registrations.register("newcomer", "newcomer@example.com", SECRET);

        assertThat(account.active()).isFalse();
        assertThat(lastMessage().body()).contains("/user/verify/");

        String token = lastMessage().body().substring(lastMessage().body().indexOf("/user/verify/") + 13);
        assertThat(registrations.verifyMail(token)).isTrue();
        assertThat(accounts.find(account.id()))
                .hasValueSatisfying(verified -> assertThat(verified.active()).isTrue());
    }

    @Test
    void aVerificationLinkWorksOnlyOnce() {
        registrationMode(UserSettings.OPEN, true);
        registrations.register("newcomer", "newcomer@example.com", SECRET);
        String token = lastMessage().body().substring(lastMessage().body().indexOf("/user/verify/") + 13);

        assertThat(registrations.verifyMail(token)).isTrue();
        assertThat(registrations.verifyMail(token)).isFalse();
    }

    @Test
    void aSiteThatTakesNoApplicationsRefusesToSignAnyoneUp() {
        registrationMode(UserSettings.ADMIN_ONLY, false);

        assertThatThrownBy(() -> registrations.register("newcomer", "newcomer@example.com", SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not take applications");
    }

    @Test
    void theRegistrationPageSaysSoWhenTheSiteTakesNoApplications() throws Exception {
        registrationMode(UserSettings.ADMIN_ONLY, false);

        String page = pageAt(AccountController.REGISTER_PATH);

        assertThat(page).contains("Only an administrator creates accounts");
        assertThat(page).doesNotContain("name=\"password\"");
    }

    @Test
    void theRegistrationPageSaysWhatWillHappenNext() throws Exception {
        registrationMode(UserSettings.ADMIN_APPROVAL, false);
        assertThat(pageAt(AccountController.REGISTER_PATH)).contains("opened by an administrator");

        registrationMode(UserSettings.OPEN, true);
        assertThat(pageAt(AccountController.REGISTER_PATH)).contains("confirm your address");

        registrationMode(UserSettings.OPEN, false);
        assertThat(pageAt(AccountController.REGISTER_PATH)).contains("name=\"password\"");
    }

    @Test
    void signingUpThroughThePageCreatesTheAccount() throws Exception {
        registrationMode(UserSettings.OPEN, false);

        String page = postForm(AccountController.REGISTER_PATH,
                "name", "newcomer", "mail", "newcomer@example.com", "password", SECRET);

        assertThat(page).contains("ready");
        assertThat(accounts.findByName("newcomer")).isPresent();
    }

    @Test
    void signingUpWithANameSomeoneElseHasSaysSo() throws Exception {
        registrationMode(UserSettings.OPEN, false);
        registrations.register("newcomer", "newcomer@example.com", SECRET);

        String page = postForm(AccountController.REGISTER_PATH,
                "name", "newcomer", "mail", "other@example.com", "password", SECRET);

        assertThat(page).contains("already belongs to an account");
    }

    @Test
    void signingUpOnASiteThatApprovesAccountsSaysToWait() throws Exception {
        registrationMode(UserSettings.ADMIN_APPROVAL, false);

        String page = postForm(AccountController.REGISTER_PATH,
                "name", "waiting", "mail", "waiting@example.com", "password", SECRET);

        assertThat(page).contains("waiting to be opened");
    }

    @Test
    void confirmingAnAddressThroughTheLinkSaysSo() throws Exception {
        registrationMode(UserSettings.OPEN, true);
        registrations.register("newcomer", "newcomer@example.com", SECRET);
        String token = lastMessage().body().substring(lastMessage().body().indexOf("/user/verify/") + 13);

        assertThat(pageAt("/user/verify/" + token)).contains("confirmed");
        assertThat(pageAt("/user/verify/" + token)).contains("used already");
    }

    @Test
    void aResetLinkSignsThePersonInOnceAndCannotBeUsedAgain() throws Exception {
        registrationMode(UserSettings.OPEN, false);
        UserAccount account = registrations.register("forgetful", "forgetful@example.com", SECRET);
        mailbox.clear();

        String token = resets.requestReset("forgetful").orElseThrow();
        assertThat(lastMessage().body()).contains(PasswordResetService.RESET_PATH + token);

        mockMvc.perform(get("/user/reset/" + token))
                .andExpect(status().isOk())
                .andExpect(authenticated().withUsername("forgetful"));

        assertThat(resets.claim(token)).isEmpty();
        assertThat(pageAt("/user/reset/" + token)).contains("used already");
        assertThat(account.id()).isPositive();
    }

    @Test
    void askingForALinkSaysTheSameThingWhoeverIsAskedAbout() throws Exception {
        registrationMode(UserSettings.OPEN, false);

        String page = postForm(AccountController.PASSWORD_PATH, "name", "nobody");

        assertThat(page).contains("If that account exists");
        assertThat(resets.requestReset("nobody")).isEmpty();
    }

    @Test
    void theNewPasswordIsTheOneThatWorksAfterwards() throws Exception {
        registrationMode(UserSettings.OPEN, false);
        registrations.register("forgetful", "forgetful@example.com", SECRET);
        String token = resets.requestReset("forgetful").orElseThrow();

        var session = mockMvc.perform(get("/user/reset/" + token))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession();

        mockMvc.perform(post("/user/reset/" + token)
                        .session((org.springframework.mock.web.MockHttpSession) session)
                        .param("password", "a whole new secret")
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(formLogin(SecurityConfig.LOGIN_PATH)
                        .user("forgetful").password("a whole new secret"))
                .andExpect(authenticated().withUsername("forgetful"));
    }

    @Test
    void aPasswordCannotBeSetWithoutHavingUsedALink() throws Exception {
        String page = postForm("/user/reset/whatever", "password", "trying it on");

        assertThat(page).contains("Sign in again");
    }

    @Test
    void thePasswordPageAsksForTheNameOnTheAccount() throws Exception {
        assertThat(pageAt(AccountController.PASSWORD_PATH)).contains("name=\"name\"");
    }

    @Test
    void aTokenIssuedForOnePurposeIsNoUseForAnother() {
        String token = oneTimeLinks.issue(1L, OneTimeLinkService.VERIFY_MAIL);

        assertThat(oneTimeLinks.consume(token, OneTimeLinkService.RESET_PASSWORD)).isEmpty();
        assertThat(oneTimeLinks.consume(token, OneTimeLinkService.VERIFY_MAIL)).contains(1L);
    }

    @Test
    void aTokenNobodyIssuedIsNoUseAtAll() {
        assertThat(oneTimeLinks.consume("made-up", OneTimeLinkService.RESET_PASSWORD)).isEmpty();
    }

    private String pageAt(String path) throws Exception {
        return mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String postForm(String path, String... params) throws Exception {
        var request = post(path).with(csrf());
        for (int index = 0; index < params.length; index += 2) {
            request = request.param(params[index], params[index + 1]);
        }
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
