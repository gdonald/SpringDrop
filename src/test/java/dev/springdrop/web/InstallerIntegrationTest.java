package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.batch.BatchManager;
import dev.springdrop.kernel.batch.BatchProgress;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.install.InstallProfile;
import dev.springdrop.kernel.install.Installer;
import dev.springdrop.kernel.module.ModuleManager;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.schema.SchemaManager;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.install.InstallChoices;
import dev.springdrop.kernel.install.InstallState;
import dev.springdrop.kernel.language.LanguageManager;
import dev.springdrop.kernel.language.LanguageSettings;
import dev.springdrop.kernel.security.SecurityConfig;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.user.UserEntityType;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = "springdrop.install.assume-installed=false")
@AutoConfigureMockMvc
@Import(InstallerIntegrationTest.TestProfiles.class)
class InstallerIntegrationTest extends AbstractIntegrationTest {

    /** A profile with a module and config of its own, and one whose config breaks its schema. */
    @TestConfiguration
    static class TestProfiles {

        @Bean
        InstallProfile testingProfile() {
            return profile("testing", List.of("widgets", "foundation"));
        }

        @Bean
        InstallProfile brokenProfile() {
            return profile("broken", List.of());
        }

        private static InstallProfile profile(String id, List<String> modules) {
            return new InstallProfile() {

                @Override
                public String id() {
                    return id;
                }

                @Override
                public String label() {
                    return id;
                }

                @Override
                public String description() {
                    return "For the installer test.";
                }

                @Override
                public int weight() {
                    return 90;
                }

                @Override
                public List<String> modules() {
                    return modules;
                }
            };
        }
    }

    @Autowired
    private Installer installer;

    @Autowired
    private ModuleManager modules;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private NodeTypeManager types;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private SchemaManager schemaManager;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InstallState state;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private ConfigStore config;

    @Autowired
    private LanguageManager languages;

    @Autowired
    private BatchManager batches;

    private final MockHttpSession session = new MockHttpSession();

    private EntityData administratorBefore;

    private Map<String, ConfigStore.StoredConfig> settingsBefore;

    @BeforeEach
    void aFreshSite() {
        state.forget();
        administratorBefore = entities.load(UserEntityType.ID, UserAccount.ADMINISTRATOR_ID).orElseThrow();
        settingsBefore = new LinkedHashMap<>();
        for (String name : new String[] {SiteInformation.CONFIG_NAME, LanguageSettings.CONFIG_NAME}) {
            ConfigStore.StoredConfig stored = config.everything().get(name);
            if (stored != null) {
                settingsBefore.put(name, stored);
            }
        }
    }

    @AfterEach
    void putTheSiteBack() {
        state.forget();
        entities.save(administratorBefore);
        for (String name : new String[] {SiteInformation.CONFIG_NAME, LanguageSettings.CONFIG_NAME}) {
            ConfigStore.StoredConfig stored = settingsBefore.get(name);
            if (stored == null) {
                config.delete(name);
            } else {
                config.saveImported(name, stored.data(), stored.uuid());
            }
        }
        languages.makeDefault("en");
        languages.delete("fr");
        languages.delete("ar");
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.session(session).with(csrf())).andReturn().getResponse();
    }

    private Document page(String path) throws Exception {
        return Jsoup.parse(send(get(path)).getContentAsString());
    }

    private MockHttpServletResponse installSite(String password) throws Exception {
        return send(post(InstallerController.SITE_PATH)
                .param(InstallerController.SITE_NAME, "Riverside Library")
                .param(InstallerController.SITE_MAIL, "web@riverside.example")
                .param(InstallerController.ADMIN_NAME, "librarian")
                .param(InstallerController.ADMIN_MAIL, "librarian@riverside.example")
                .param(InstallerController.PASSWORD, password));
    }

    @Test
    void aFreshDatabaseBecomesARunningSiteWithAnAdminAccountThroughTheInstaller() throws Exception {
        assertThat(send(get("/")).getRedirectedUrl()).isEqualTo(InstallerController.PATH);

        assertThat(page(InstallerController.PATH).selectFirst("select[name=langcode] option[selected]").val())
                .isEqualTo("en");
        assertThat(send(post(InstallerController.PATH).param(InstallerController.LANGCODE, "fr"))
                .getRedirectedUrl()).isEqualTo(InstallerController.REQUIREMENTS_PATH);

        Document requirements = page(InstallerController.REQUIREMENTS_PATH);
        assertThat(requirements.select("table.install-requirements tr")).hasSize(4)
                .allSatisfy(row -> assertThat(row.attr("data-met")).isEqualTo("true"));
        assertThat(requirements.selectFirst("a.btn-primary").attr("href")).isEqualTo(InstallerController.PROFILE_PATH);

        assertThat(page(InstallerController.PROFILE_PATH).select("input[name=profile]").eachAttr("value"))
                .contains("minimal");
        assertThat(send(post(InstallerController.PROFILE_PATH).param(InstallerController.PROFILE, "minimal"))
                .getRedirectedUrl()).isEqualTo(InstallerController.SITE_PATH);

        assertThat(page(InstallerController.SITE_PATH).selectFirst("input[name=password]")).isNotNull();
        String batch = installSite("correct horse battery").getRedirectedUrl();
        assertThat(batch).startsWith("/batch/");
        assertThat(batches.processAll(batch.substring("/batch/".length())).errors()).isEmpty();

        assertThat(state.installed()).isTrue();
        assertThat(send(get("/")).getStatus()).isEqualTo(200);
        assertThat(send(get(InstallerController.PATH)).getRedirectedUrl()).isEqualTo("/");
        EntityData administrator = entities.load(UserEntityType.ID, UserAccount.ADMINISTRATOR_ID).orElseThrow();
        assertThat(administrator.label()).isEqualTo("librarian");
        assertThat(administrator.fields()).containsEntry(UserEntityType.MAIL, "librarian@riverside.example");
        assertThat(config.read(SiteInformation.CONFIG_NAME, SiteInformation.class, null).name())
                .isEqualTo("Riverside Library");
        assertThat(languages.defaultLangcode()).isEqualTo("fr");
        mockMvc.perform(formLogin(SecurityConfig.LOGIN_PATH).user("librarian").password("correct horse battery"))
                .andExpect(authenticated().withUsername("librarian"));
    }

    private Installer.SiteDetails details() {
        return new Installer.SiteDetails("en", "Test kitchen", "web@kitchen.example", "chef",
                "chef@kitchen.example", "{noop}secret-recipe");
    }

    @Test
    void aProfileEnablesItsModulesWithTheirDependenciesAndImportsItsConfigInOrder() {
        InstallProfile testing = installer.profile("testing").orElseThrow();
        try {
            assertThat(installer.moduleOrder(testing)).containsExactly("foundation", "widgets");

            BatchProgress progress = batches.processAll(installer.start(testing, new Installer.SiteDetails("ar",
                    "Test kitchen", "web@kitchen.example", "chef", "chef@kitchen.example", "{noop}secret-recipe"), "/")
                    .batchId());

            assertThat(progress.errors()).isEmpty();
            assertThat(languages.find("ar").orElseThrow().direction()).isEqualTo("rtl");
            assertThat(modules.isEnabled("widgets")).isTrue();
            assertThat(modules.isEnabled("foundation")).isTrue();
            assertThat(fields.findInstance("node", "recipe", "ingredients")).isPresent();
            assertThat(types.find("recipe")).isPresent();
        } finally {
            fields.findStorage("node", "ingredients").ifPresent(storage -> fields.deleteStorage("node", "ingredients"));
            types.find("recipe").ifPresent(type -> types.delete("recipe"));
            dsl.deleteFrom(DSL.table("module")).execute();
            schemaManager.dropTable("widgets_data");
        }
    }

    @Test
    void aProfileConfigObjectThatBreaksItsSchemaFailsItsOperation() {
        BatchProgress progress = batches.processAll(installer.start(installer.profile("broken").orElseThrow(),
                details(), "/").batchId());

        assertThat(progress.errors()).anySatisfy(error -> assertThat(error)
                .contains("user.role.cook: weight holds heavy, which is not a whole number."));
    }

    @Test
    void aProfileNamingAModuleTheSiteDoesNotHaveCannotBeInstalled() {
        InstallProfile missing = TestProfiles.profile("missing", List.of("nonexistent"));

        assertThatThrownBy(() -> installer.moduleOrder(missing))
                .hasMessageContaining("nonexistent");
    }

    @Test
    void aMinimalInstallEnablesOnlyCoreAndAddsNoContentConfiguration() {
        Set<String> configBefore = config.everything().keySet();

        BatchProgress progress = batches.processAll(installer.start(installer.profile("minimal").orElseThrow(),
                details(), "/").batchId());

        assertThat(progress.errors()).isEmpty();
        assertThat(dsl.fetchCount(DSL.table("module"))).isZero();
        Set<String> added = new TreeSet<>(config.everything().keySet());
        added.removeAll(configBefore);
        assertThat(added).isSubsetOf(SiteInformation.CONFIG_NAME, LanguageSettings.CONFIG_NAME);
        assertThat(state.installed()).isTrue();
    }

    @Test
    void eachStepWaitsForTheStepsBeforeIt() throws Exception {
        assertThat(send(get(InstallerController.REQUIREMENTS_PATH)).getRedirectedUrl())
                .isEqualTo(InstallerController.PATH);
        assertThat(send(get(InstallerController.PROFILE_PATH)).getRedirectedUrl()).isEqualTo(InstallerController.PATH);
        assertThat(send(post(InstallerController.PROFILE_PATH)).getRedirectedUrl()).isEqualTo(InstallerController.PATH);
        assertThat(send(get(InstallerController.SITE_PATH)).getRedirectedUrl()).isEqualTo(InstallerController.PATH);
        assertThat(send(post(InstallerController.SITE_PATH)).getRedirectedUrl()).isEqualTo(InstallerController.PATH);

        session.setAttribute(InstallerController.CHOICES, InstallChoices.NONE.withLangcode("en"));
        assertThat(send(get(InstallerController.SITE_PATH)).getRedirectedUrl())
                .isEqualTo(InstallerController.PROFILE_PATH);
        assertThat(page(InstallerController.PATH).selectFirst("select[name=langcode] option[selected]").val())
                .isEqualTo("en");
    }

    @Test
    void choicesThatAreNotOfferedAndDetailsThatFailTheirChecksAreRefused() throws Exception {
        assertThat(send(post(InstallerController.PATH).param(InstallerController.LANGCODE, "klingon"))
                .getContentAsString()).contains("Choose a language.");
        send(post(InstallerController.PATH).param(InstallerController.LANGCODE, "en"));
        assertThat(send(post(InstallerController.PROFILE_PATH).param(InstallerController.PROFILE, "everything"))
                .getContentAsString()).contains("Choose a profile.");
        send(post(InstallerController.PROFILE_PATH).param(InstallerController.PROFILE, "minimal"));
        assertThat(page(InstallerController.PROFILE_PATH).selectFirst("input[name=profile][checked]").val())
                .isEqualTo("minimal");

        Document refused = Jsoup.parse(installSite("short").getContentAsString());

        assertThat(refused.text()).contains("Use at least 8 characters.");
        assertThat(state.installed()).isFalse();
    }

    @Test
    void filesPagesLoadAndTheBatchRunBeforeTheSiteIsInstalled() throws Exception {
        assertThat(send(get("/js/form-states.js")).getRedirectedUrl()).isNull();
        assertThat(send(get("/error")).getRedirectedUrl()).isNull();
        assertThat(send(get("/user/login")).getRedirectedUrl()).isEqualTo(InstallerController.PATH);
    }
}
