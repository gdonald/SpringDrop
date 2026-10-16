package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jooq.impl.DSL.table;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.module.ModuleManager;
import dev.springdrop.kernel.schema.SchemaManager;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.support.AbstractIntegrationTest;
import org.jooq.DSLContext;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class ModulesIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ModuleManager modules;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private SchemaManager schemaManager;

    private final MockHttpSession session = new MockHttpSession();

    @BeforeEach
    @AfterEach
    void noModules() {
        dsl.deleteFrom(table("module")).execute();
        schemaManager.dropTable("widgets_data");
    }

    private static RequestPostProcessor administrator() {
        return user("admin").authorities(new SimpleGrantedAuthority(Permissions.ADMINISTER_MODULES));
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.session(session).with(csrf()).with(administrator())).andReturn().getResponse();
    }

    private Document page(MockHttpServletRequestBuilder request) throws Exception {
        return Jsoup.parse(send(request).getContentAsString());
    }

    @Test
    void enablingAModuleFromThePageActivatesItPullingInDependenciesWithConfirmation() throws Exception {
        Document confirm = page(post(ModulesController.PATH).param(ModulesController.ENABLE_PREFIX + "widgets", "true"));

        assertThat(confirm.selectFirst(".modules-confirm").text())
                .isEqualTo("Enabling Widgets also enables Foundation, which it needs.");
        assertThat(modules.isEnabled("widgets")).isFalse();

        MockHttpServletResponse confirmed = send(post(ModulesController.CONFIRM_PATH).param(ModulesController.MODULES,
                confirm.selectFirst("input[name=modules]").val()));

        assertThat(confirmed.getRedirectedUrl()).isEqualTo(ModulesController.PATH);
        assertThat(modules.isEnabled("widgets")).isTrue();
        assertThat(modules.isEnabled("foundation")).isTrue();
        assertThat(page(get(ModulesController.PATH)).selectFirst(".status-messages .alert-success").text())
                .isEqualTo("The configuration options have been saved.");
    }

    @Test
    void theModulesAreListedByPackageWithWhatEachNeedsAndWhatNeedsIt() throws Exception {
        Document list = page(get(ModulesController.PATH));

        assertThat(list.select("fieldset[data-package]").eachAttr("data-package"))
                .contains("Content", "Core", "Other");
        assertThat(list.select("fieldset[data-package=Core] tr[data-module]").eachAttr("data-module"))
                .containsExactly("content", "foundation");
        assertThat(list.selectFirst("tr[data-module=blog]").text()).contains("Requires: Content, Foundation");
        assertThat(list.selectFirst("tr[data-module=foundation]").text()).contains("Required by: Blog, Content, Widgets");
        assertThat(list.selectFirst("input[name=enable_foundation]").hasAttr("checked")).isFalse();
    }

    @Test
    void aModuleAnEnabledModuleNeedsCannotBeTurnedOff() throws Exception {
        modules.enable("content");

        Document list = page(get(ModulesController.PATH));
        assertThat(list.selectFirst("input[type=checkbox][name=enable_foundation]").hasAttr("disabled")).isTrue();
        assertThat(list.selectFirst("input[type=hidden][name=enable_foundation]")).isNotNull();

        send(post(ModulesController.PATH).param(ModulesController.ENABLE_PREFIX + "content", "true"));
        assertThat(page(get(ModulesController.PATH)).selectFirst(".status-messages .alert-danger").text())
                .contains("Cannot disable 'foundation'");
        assertThat(modules.isEnabled("foundation")).isTrue();
    }

    @Test
    void untickingAModuleDisablesItAndTickingOneWhoseNeedsAreOnEnablesItAtOnce() throws Exception {
        modules.enable("foundation");

        assertThat(send(post(ModulesController.PATH).param(ModulesController.ENABLE_PREFIX + "foundation", "true")
                .param(ModulesController.ENABLE_PREFIX + "content", "true")).getRedirectedUrl())
                .isEqualTo(ModulesController.PATH);
        assertThat(modules.isEnabled("content")).isTrue();

        send(post(ModulesController.PATH).param(ModulesController.ENABLE_PREFIX + "foundation", "true"));

        assertThat(modules.isEnabled("content")).isFalse();
        assertThat(modules.isEnabled("foundation")).isTrue();
    }

    @Test
    void theConfirmationNamesEachNeededModuleOnceAndLeavesOutThoseTickedAlongside() throws Exception {
        Document both = page(post(ModulesController.PATH).param(ModulesController.ENABLE_PREFIX + "blog", "true"));
        assertThat(both.selectFirst(".modules-confirm").text())
                .isEqualTo("Enabling Blog also enables Content, Foundation, which it needs.");

        Document one = page(post(ModulesController.PATH).param(ModulesController.ENABLE_PREFIX + "blog", "true")
                .param(ModulesController.ENABLE_PREFIX + "content", "true"));
        assertThat(one.selectFirst(".modules-confirm").text())
                .isEqualTo("Enabling Content, Blog also enables Foundation, which it needs.");
    }

    @Test
    void confirmingWithNothingChosenChangesNothing() throws Exception {
        assertThat(send(post(ModulesController.CONFIRM_PATH)).getRedirectedUrl()).isEqualTo(ModulesController.PATH);
        assertThat(modules.isEnabled("foundation")).isFalse();
    }

    @Test
    void eachSectionShowsItsHelp() throws Exception {
        assertThat(page(get(ModulesController.PATH)).selectFirst(".page-help").text()).startsWith("Modules add");
        RequestPostProcessor siteAdministrator = user("admin").authorities(
                new SimpleGrantedAuthority(Permissions.ADMINISTER_SITE_CONFIGURATION));
        assertThat(Jsoup.parse(mockMvc.perform(get(RssSettingsController.PATH).with(siteAdministrator)).andReturn()
                .getResponse().getContentAsString()).selectFirst(".page-help").text()).startsWith("Configuration");
    }

    @Test
    void thePageNeedsTheAdministerModulesPermission() throws Exception {
        assertThat(mockMvc.perform(get(ModulesController.PATH).with(user("editor"))).andReturn().getResponse()
                .getStatus()).isEqualTo(403);
    }
}
