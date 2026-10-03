package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.contact.ContactEntityType;
import dev.springdrop.kernel.contact.ContactForm;
import dev.springdrop.kernel.contact.ContactFormManager;
import dev.springdrop.kernel.contact.ContactPermissions;
import dev.springdrop.kernel.contact.ContactService;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.flood.FloodService;
import dev.springdrop.kernel.flood.FloodSettings;
import dev.springdrop.kernel.mail.CollectingMailBackend;
import dev.springdrop.kernel.mail.MailMessage;
import dev.springdrop.kernel.role.RoleConfig;
import dev.springdrop.kernel.role.RoleManager;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.kernel.user.UserEntityType;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class ContactIntegrationTest extends AbstractIntegrationTest {

    private static final String FEEDBACK = "feedback";

    private static final String ANONYMOUS_SENDER = "ip:127.0.0.1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContactFormManager forms;

    @Autowired
    private CollectingMailBackend mailbox;

    @Autowired
    private FloodService flood;

    @Autowired
    private RoleManager roles;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private ConfigStore configStore;

    private UserAccount edith;

    @BeforeEach
    void aFeedbackFormAndAnAccountThatTakesMessages() {
        accounts.install();
        roles.install();
        clear();
        forms.installPersonal();
        forms.save(new ContactForm(FEEDBACK, "Website feedback", List.of("team@example.com", "lead@example.com"),
                "Thanks for writing.", "/thanks", 0, false));
        roles.grant(RoleConfig.ANONYMOUS, ContactPermissions.SITE_WIDE);
        roles.grant(RoleConfig.ANONYMOUS, ContactPermissions.PERSONAL);
        edith = accounts.create("edith", "edith@example.com", "");
        configStore.save(SiteInformation.CONFIG_NAME, new SiteInformation("Riverside", "", "site@example.com",
                "https://riverside.example"));
    }

    @AfterEach
    void nothingLeft() {
        clear();
        configStore.delete(SiteInformation.CONFIG_NAME);
    }

    private void clear() {
        mailbox.clear();
        flood.clear(FloodSettings.CONTACT, ANONYMOUS_SENDER);
        flood.clear(FloodSettings.CONTACT, "account:7");
        queries.query(ContactEntityType.ID).ids().forEach(id -> entities.delete(ContactEntityType.ID, id));
        fields.findStorage(ContactEntityType.ID, "phone")
                .ifPresent(storage -> fields.deleteStorage(ContactEntityType.ID, "phone"));
        forms.all().stream().filter(form -> !form.personal()).forEach(form -> forms.delete(form.id()));
        accounts.findByName("edith").ifPresent(account -> entities.delete(UserEntityType.ID, account.id()));
        entities.delete(UserEntityType.ID, 7L);
        roles.find(RoleConfig.ANONYMOUS).ifPresent(anonymous -> anonymous.permissions()
                .forEach(permission -> roles.revoke(RoleConfig.ANONYMOUS, permission)));
    }

    private static MockHttpServletRequestBuilder message(String path) {
        return post(path).with(csrf())
                .param(ContactController.NAME, "Visitor")
                .param(ContactController.MAIL, "visitor@example.com")
                .param(ContactController.SUBJECT, "Opening hours")
                .param(ContactController.MESSAGE, "Are you open on Sunday? Mention [site:name].");
    }

    private static RequestPostProcessor signedIn(long id, String... granted) {
        return user(new AccountPrincipal(id, "account" + id, "", true, List.of(granted)));
    }

    private List<MailMessage> mailTo(String address) {
        return mailbox.delivered().stream().filter(mail -> mail.to().equals(address)).toList();
    }

    private Document page(MockHttpServletRequestBuilder request) throws Exception {
        return Jsoup.parse(mockMvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void aSubmissionMailsTheRecipientsAndIsLimitedEachHour() throws Exception {
        mockMvc.perform(message(ContactController.PATH + "/" + FEEDBACK)).andExpect(redirectedUrl("/thanks"));

        MailMessage toTeam = mailTo("team@example.com").getFirst();
        assertThat(toTeam.subject()).isEqualTo("[Website feedback] Opening hours");
        assertThat(toTeam.replyTo()).isEqualTo("visitor@example.com");
        assertThat(toTeam.body()).contains("Visitor (visitor@example.com) sent a message",
                "Are you open on Sunday? Mention [site:name].");
        assertThat(mailTo("lead@example.com")).hasSize(1);
        assertThat(mailTo("visitor@example.com")).extracting(MailMessage::body).containsExactly("Thanks for writing.");
        assertThat(queries.query(ContactEntityType.ID).count()).isEqualTo(1);

        for (int sent = 1; sent < FloodSettings.CONTACT_THRESHOLD; sent++) {
            mockMvc.perform(message(ContactController.PATH + "/" + FEEDBACK)).andExpect(status().is3xxRedirection());
        }
        mailbox.clear();
        Document refused = page(message(ContactController.PATH + "/" + FEEDBACK));

        assertThat(refused.text()).contains("You cannot send more than 5 messages in an hour.");
        assertThat(mailbox.delivered()).isEmpty();
    }

    @Nested
    class SiteWideForms {

        @Test
        void contactShowsTheSelectedFormOrElseTheFirst() throws Exception {
            forms.save(new ContactForm("sales", "Sales", List.of("sales@example.com"), "", "", -1, false));
            assertThat(page(get(ContactController.PATH)).selectFirst("form").attr("action"))
                    .isEqualTo(ContactController.PATH + "/sales");

            forms.save(forms.find(FEEDBACK).orElseThrow().asSelected());
            assertThat(page(get(ContactController.PATH)).selectFirst("form").attr("action"))
                    .isEqualTo(ContactController.PATH + "/" + FEEDBACK);
        }

        @Test
        void aSiteWithNoFormHasNoContactPage() throws Exception {
            forms.delete(FEEDBACK);

            mockMvc.perform(get(ContactController.PATH)).andExpect(status().isNotFound());
            mockMvc.perform(get(ContactController.PATH + "/" + ContactForm.PERSONAL)).andExpect(status().isNotFound());
        }

        @Test
        void aSubmissionMissingWhatItNeedsSendsNothing() throws Exception {
            Document document = page(post(ContactController.PATH + "/" + FEEDBACK).with(csrf())
                    .param(ContactController.NAME, "Visitor")
                    .param(ContactController.MAIL, "not an address")
                    .param(ContactController.SUBJECT, "")
                    .param(ContactController.MESSAGE, "Hello."));

            assertThat(document.select(".is-invalid").eachAttr("name"))
                    .containsExactlyInAnyOrder(ContactController.MAIL, ContactController.SUBJECT);
            assertThat(mailbox.delivered()).isEmpty();
        }

        @Test
        void someoneSignedInSendsAsTheirAccountAndMayHaveACopy() throws Exception {
            RequestPostProcessor member = signedIn(edith.id(), ContactPermissions.SITE_WIDE);
            assertThat(page(get(ContactController.PATH + "/" + FEEDBACK).with(member))
                    .select("input[name=" + ContactController.NAME + "]")).isEmpty();
            forms.save(forms.find(FEEDBACK).orElseThrow().withReply("").withRedirect(""));

            mockMvc.perform(post(ContactController.PATH + "/" + FEEDBACK).with(member).with(csrf())
                            .param(ContactController.SUBJECT, "Opening hours")
                            .param(ContactController.MESSAGE, "Hello.")
                            .param(ContactController.COPY, "true"))
                    .andExpect(redirectedUrl(HomeController.PATH));

            assertThat(mailTo("team@example.com").getFirst().replyTo()).isEqualTo("edith@example.com");
            assertThat(mailTo("edith@example.com")).extracting(MailMessage::body)
                    .singleElement().asString().startsWith(ContactService.COPY_NOTE);
            flood.clear(FloodSettings.CONTACT, "account:" + edith.id());
        }

        @Test
        void someoneWhoAdministersContactFormsIsNotLimited() throws Exception {
            RequestPostProcessor administrator = signedIn(7L, ContactPermissions.SITE_WIDE,
                    ContactPermissions.ADMINISTER);
            accountSeven();
            for (int sent = 0; sent <= FloodSettings.CONTACT_THRESHOLD; sent++) {
                mockMvc.perform(post(ContactController.PATH + "/" + FEEDBACK).with(administrator).with(csrf())
                                .param(ContactController.SUBJECT, "Note " + sent)
                                .param(ContactController.MESSAGE, "Hello."))
                        .andExpect(status().is3xxRedirection());
            }
            assertThat(mailTo("team@example.com")).hasSize(FloodSettings.CONTACT_THRESHOLD + 1);
        }

        @Test
        void aFormsMessagesCarryItsFieldsAndTheirConstraints() throws Exception {
            fields.createStorage(new FieldStorageConfig("phone", ContactEntityType.ID, StringFieldType.ID, 1,
                    Map.of("max_length", 12)));
            fields.createInstance(FieldInstanceConfig.of("phone", ContactEntityType.ID, FEEDBACK, "Phone"));

            Document refused = page(message(ContactController.PATH + "/" + FEEDBACK).param("phone", "0".repeat(13)));
            assertThat(refused.selectFirst("input[name=phone]").hasClass("is-invalid")).isTrue();

            mockMvc.perform(message(ContactController.PATH + "/" + FEEDBACK).param("phone", "555-0100"))
                    .andExpect(status().is3xxRedirection());
            Object stored = queries.query(ContactEntityType.ID).ids().getFirst();
            assertThat(entities.load(ContactEntityType.ID, stored).orElseThrow().fields())
                    .containsEntry("phone", "555-0100")
                    .containsEntry(ContactEntityType.IP, "127.0.0.1");
        }

        @Test
        void someoneWithoutThePermissionCannotUseTheForm() throws Exception {
            roles.revoke(RoleConfig.ANONYMOUS, ContactPermissions.SITE_WIDE);

            mockMvc.perform(get(ContactController.PATH + "/" + FEEDBACK)).andExpect(status().isForbidden());
        }
    }

    @Nested
    class PersonalForms {

        @Test
        void aPersonalMessageReachesSomeoneWhoAcceptsThemWithoutTheSendersAddress() throws Exception {
            assertThat(page(get(ContactController.personalPath(edith.id()))).selectFirst("h1").text())
                    .isEqualTo("Contact edith");

            mockMvc.perform(message(ContactController.personalPath(edith.id())))
                    .andExpect(redirectedUrl(HomeController.PATH));

            MailMessage delivered = mailTo("edith@example.com").getFirst();
            assertThat(delivered.subject()).isEqualTo("[Riverside] Opening hours");
            assertThat(delivered.replyTo()).isEqualTo("site@example.com");
            assertThat(delivered.body()).contains("Hello edith,", "Visitor has sent you a message",
                    "Are you open on Sunday?").doesNotContain("visitor@example.com");
            assertThat(queries.query(ContactEntityType.ID).ids()).singleElement().satisfies(id ->
                    assertThat(entities.load(ContactEntityType.ID, id).orElseThrow().fields())
                            .containsEntry(ContactEntityType.RECIPIENT, edith.id()));
        }

        @Test
        void aPersonalMessageIsRefusedForSomeoneWhoTurnedTheirFormOff() throws Exception {
            mockMvc.perform(post(ContactController.SETTINGS_PATH).with(signedIn(edith.id())).with(csrf()))
                    .andExpect(redirectedUrl(ContactController.SETTINGS_PATH));

            mockMvc.perform(get(ContactController.personalPath(edith.id()))).andExpect(status().isForbidden());
            mockMvc.perform(message(ContactController.personalPath(edith.id()))).andExpect(status().isForbidden());
            assertThat(mailbox.delivered()).isEmpty();
        }

        @Test
        void aSenderSignedInMayHaveACopyOfTheirPersonalMessage() throws Exception {
            accountSeven();
            RequestPostProcessor wilma = signedIn(7L, ContactPermissions.PERSONAL);

            mockMvc.perform(post(ContactController.personalPath(edith.id())).with(wilma).with(csrf())
                            .param(ContactController.SUBJECT, "Lunch")
                            .param(ContactController.MESSAGE, "Tuesday?")
                            .param(ContactController.COPY, "true"))
                    .andExpect(status().is3xxRedirection());

            assertThat(mailTo("wilma@example.com")).extracting(MailMessage::body).singleElement().asString()
                    .contains(ContactService.COPY_NOTE, "Tuesday?");
        }

        @Test
        void aBlockedOrUnknownAccountOrAnonymousTakesNoMessages() throws Exception {
            accounts.block(edith.id());

            mockMvc.perform(get(ContactController.personalPath(edith.id()))).andExpect(status().isForbidden());
            mockMvc.perform(get(ContactController.personalPath(999_999))).andExpect(status().isNotFound());
            mockMvc.perform(get(ContactController.personalPath(UserAccount.ANONYMOUS_ID)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void withoutThePersonalFormThereAreNoPersonalMessages() throws Exception {
            forms.delete(ContactForm.PERSONAL);
            try {
                mockMvc.perform(get(ContactController.personalPath(edith.id()))).andExpect(status().isNotFound());
            } finally {
                forms.installPersonal();
            }
        }

        @Test
        void anAccountThatNeverSaidAcceptsMessages() throws Exception {
            EntityData account = accounts.load(edith.id()).orElseThrow();
            entities.save(new EntityData(UserEntityType.ID, account.id(), account.uuid(), null, account.label(),
                    account.langcode(), null, Map.of(UserEntityType.MAIL, "edith@example.com", "status", true)));

            mockMvc.perform(get(ContactController.personalPath(edith.id()))).andExpect(status().isOk());
        }
    }

    @Nested
    class Settings {

        @Test
        void aPersonTurnsTheirOwnFormOffAndOnAgain() throws Exception {
            RequestPostProcessor self = signedIn(edith.id());
            assertThat(page(get(ContactController.SETTINGS_PATH).with(self))
                    .selectFirst("input[name=" + ContactController.ACCEPTS + "]").hasAttr("checked")).isTrue();

            mockMvc.perform(post(ContactController.SETTINGS_PATH).with(self).with(csrf()));
            assertThat(accounts.load(edith.id()).orElseThrow().fields()).containsEntry(UserEntityType.CONTACT, false);
            mockMvc.perform(post(ContactController.SETTINGS_PATH).with(self).with(csrf())
                    .param(ContactController.ACCEPTS, "true"));
            assertThat(accounts.load(edith.id()).orElseThrow().fields()).containsEntry(UserEntityType.CONTACT, true);
        }

        @Test
        void someoneNotSignedInHasNoContactSettings() throws Exception {
            mockMvc.perform(get(ContactController.SETTINGS_PATH)).andExpect(status().isForbidden());
            mockMvc.perform(get(ContactController.SETTINGS_PATH).with(signedIn(999_999L)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void anAdministratorSetsWhetherAnAccountTakesMessages() throws Exception {
            RequestPostProcessor administrator = user("admin").authorities(
                    new SimpleGrantedAuthority(Permissions.ADMINISTER_USERS));
            assertThat(page(get(PeopleController.PATH + "/" + edith.id() + "/edit").with(administrator))
                    .selectFirst("input[name=" + PeopleController.CONTACT + "]").hasAttr("checked")).isTrue();

            mockMvc.perform(post(PeopleController.PATH + "/" + edith.id() + "/edit").with(administrator).with(csrf()));

            assertThat(accounts.load(edith.id()).orElseThrow().fields()).containsEntry(UserEntityType.CONTACT, false);
            assertThat(page(get(PeopleController.PATH + "/" + edith.id() + "/edit").with(administrator))
                    .selectFirst("input[name=" + PeopleController.CONTACT + "]").hasAttr("checked")).isFalse();
        }
    }

    @Nested
    class Administering {

        private RequestPostProcessor administrator() {
            return user("admin").authorities(new SimpleGrantedAuthority(ContactPermissions.ADMINISTER));
        }

        private Document adminPage(MockHttpServletRequestBuilder request) throws Exception {
            return page(request.with(administrator()));
        }

        @Test
        void theFormsAreListedWithTheirRecipientsAndActionsAsButtons() throws Exception {
            Document document = adminPage(get(ContactFormAdminController.PATH));

            assertThat(document.selectFirst("[data-form=" + FEEDBACK + "]").text())
                    .contains("Website feedback", "team@example.com, lead@example.com", "No");
            assertThat(document.selectFirst("[data-form=" + ContactForm.PERSONAL + "]").text())
                    .contains("Selected user");
            assertThat(document.select("[data-form=" + ContactForm.PERSONAL + "] a:contains(Delete)")).isEmpty();
            BootstrapAssertions.assertNoOutlineButtons(document);
            BootstrapAssertions.assertEditControlsAreButtons(document);
            mockMvc.perform(get(ContactFormAdminController.PATH).with(user("visitor")))
                    .andExpect(status().isForbidden());
        }

        @Test
        void aFormIsAddedAndMadeTheDefaultInPlaceOfAnother() throws Exception {
            forms.save(forms.find(FEEDBACK).orElseThrow().asSelected());
            assertThat(adminPage(get(ContactFormAdminController.PATH + "/add"))
                    .select("textarea[name=" + ContactFormAdminController.RECIPIENTS + "]")).isNotEmpty();

            mockMvc.perform(post(ContactFormAdminController.PATH + "/add").with(administrator()).with(csrf())
                            .param(ContactFormAdminController.LABEL, "Press")
                            .param(ContactFormAdminController.RECIPIENTS, "press@example.com , desk@example.com")
                            .param(ContactFormAdminController.WEIGHT, "3")
                            .param(ContactFormAdminController.SELECTED, "true"))
                    .andExpect(redirectedUrl(ContactFormAdminController.PATH));

            assertThat(forms.find("press")).hasValueSatisfying(form -> {
                assertThat(form.recipients()).containsExactly("press@example.com", "desk@example.com");
                assertThat(form.weight()).isEqualTo(3);
                assertThat(form.selected()).isTrue();
            });
            assertThat(forms.find(FEEDBACK)).map(ContactForm::selected).contains(false);
            assertThat(adminPage(get(ContactFormAdminController.managePath("press")))
                    .selectFirst("input[name=" + ContactFormAdminController.SELECTED + "]").hasAttr("checked")).isTrue();
            forms.save(forms.find("press").orElseThrow());
            assertThat(forms.find("press")).map(ContactForm::selected).contains(true);
        }

        @Test
        void aFormWithoutRecipientsOrWithABadPathIsNotSaved() throws Exception {
            Document document = adminPage(post(ContactFormAdminController.PATH + "/add").with(csrf())
                    .param(ContactFormAdminController.LABEL, "")
                    .param(ContactFormAdminController.RECIPIENTS, "press@example.com, nobody")
                    .param(ContactFormAdminController.REDIRECT, "https://elsewhere.example"));

            assertThat(document.select(".is-invalid").eachAttr("name")).containsExactlyInAnyOrder(
                    ContactFormAdminController.LABEL, ContactFormAdminController.RECIPIENTS,
                    ContactFormAdminController.REDIRECT);
        }

        @Test
        void aFormIsEditedWithWhatItHolds() throws Exception {
            Document form = adminPage(get(ContactFormAdminController.managePath(FEEDBACK)));
            assertThat(form.selectFirst("textarea[name=" + ContactFormAdminController.RECIPIENTS + "]").text())
                    .isEqualTo("team@example.com, lead@example.com");

            mockMvc.perform(post(ContactFormAdminController.managePath(FEEDBACK)).with(administrator()).with(csrf())
                    .param(ContactFormAdminController.LABEL, "Feedback")
                    .param(ContactFormAdminController.RECIPIENTS, "team@example.com"));

            assertThat(forms.find(FEEDBACK)).hasValueSatisfying(saved -> {
                assertThat(saved.label()).isEqualTo("Feedback");
                assertThat(saved.recipients()).containsExactly("team@example.com");
                assertThat(saved.reply()).isEmpty();
                assertThat(saved.weight()).isZero();
            });
        }

        @Test
        void thePersonalFormSetsOnlyItsLabelAndAutoReplyAndCannotBeDeleted() throws Exception {
            Document form = adminPage(get(ContactFormAdminController.managePath(ContactForm.PERSONAL)));
            assertThat(form.select("textarea[name=" + ContactFormAdminController.RECIPIENTS + "]")).isEmpty();

            mockMvc.perform(post(ContactFormAdminController.managePath(ContactForm.PERSONAL)).with(administrator())
                    .with(csrf())
                    .param(ContactFormAdminController.LABEL, "Personal messages")
                    .param(ContactFormAdminController.REPLY, "Your message is on its way."));

            assertThat(forms.find(ContactForm.PERSONAL)).hasValueSatisfying(saved -> {
                assertThat(saved.label()).isEqualTo("Personal messages");
                assertThat(saved.recipients()).isEmpty();
                assertThat(saved.selected()).isFalse();
            });
            mockMvc.perform(get(ContactFormAdminController.managePath(ContactForm.PERSONAL) + "/delete")
                    .with(administrator())).andExpect(status().isNotFound());
            forms.save(ContactForm.of(ContactForm.PERSONAL, "Personal contact form", List.of()));
        }

        @Test
        void aFormIsDeletedOnceConfirmed() throws Exception {
            assertThat(adminPage(get(ContactFormAdminController.managePath(FEEDBACK) + "/delete"))
                    .select("button[type=submit]").text()).isEqualTo("Delete contact form");

            mockMvc.perform(post(ContactFormAdminController.managePath(FEEDBACK) + "/delete").with(administrator())
                    .with(csrf())).andExpect(redirectedUrl(ContactFormAdminController.PATH));

            assertThat(forms.find(FEEDBACK)).isEmpty();
            mockMvc.perform(get(ContactFormAdminController.managePath(FEEDBACK)).with(administrator()))
                    .andExpect(status().isNotFound());
        }
    }

    private void accountSeven() {
        if (accounts.find(7L).isEmpty()) {
            entities.save(new EntityData(UserEntityType.ID, 7L, null, null, "wilma", EntityData.DEFAULT_LANGCODE,
                    null, Map.of(UserEntityType.MAIL, "wilma@example.com", "status", true)));
        }
    }
}
