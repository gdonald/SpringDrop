package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.config.sync.ConfigFiles;
import dev.springdrop.kernel.config.sync.ConfigSync;
import dev.springdrop.kernel.config.sync.ConfigSyncProperties;
import dev.springdrop.kernel.role.RoleConfig;
import dev.springdrop.kernel.role.RoleManager;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.support.AbstractIntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
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
class ConfigSyncControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String PASTRY = "pastry_chef";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConfigSync sync;

    @Autowired
    private ConfigFiles files;

    @Autowired
    private ConfigSyncProperties properties;

    @Autowired
    private RoleManager roles;

    private Path directory;

    @BeforeEach
    void anExportedSite() {
        directory = Path.of(properties.syncDirectory());
        roles.save(RoleConfig.of(PASTRY, "Pastry chef", 6));
        sync.export(directory);
    }

    @AfterEach
    void nothingLeft() {
        roles.find(PASTRY).ifPresent(role -> roles.delete(role.id()));
        files.clear(directory);
    }

    private static RequestPostProcessor synchronizer() {
        return user("admin").authorities(new SimpleGrantedAuthority(Permissions.SYNCHRONIZE_CONFIGURATION));
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(csrf()).with(synchronizer())).andReturn().getResponse();
    }

    private Document page(MockHttpServletRequestBuilder request) throws Exception {
        return Jsoup.parse(send(request).getContentAsString());
    }

    @Test
    void aSiteMatchingTheSyncDirectoryHasNothingToImport() throws Exception {
        Document overview = page(get(ConfigSyncController.PATH));

        assertThat(overview.selectFirst(".no-changes")).isNotNull();
        assertThat(overview.select("form[action$=/import]")).isEmpty();
    }

    @Test
    void eachChangeIsListedWithItsDifferencesAndImportedThroughABatch() throws Exception {
        roles.save(RoleConfig.of(PASTRY, "Head pastry chef", 6));
        String name = RoleConfig.configName(PASTRY);

        Document overview = page(get(ConfigSyncController.PATH));
        assertThat(overview.selectFirst("tr[data-name=" + name + "]").attr("data-operation")).isEqualTo("UPDATE");
        String diffLink = overview.selectFirst("tr[data-name=" + name + "] a.btn").attr("href");
        Document diff = page(get(diffLink));
        assertThat(diff.selectFirst("pre.config-active").text()).contains("Head pastry chef");
        assertThat(diff.selectFirst("pre.config-staged").text()).contains("label: \"Pastry chef\"");

        String batch = send(post(ConfigSyncController.IMPORT_PATH)).getRedirectedUrl();
        assertThat(batch).startsWith("/batch/");
        send(post(batch + "/run"));

        assertThat(roles.find(PASTRY).orElseThrow().label()).isEqualTo("Pastry chef");
    }

    @Test
    void aDeletionAndARenameAreListedWithoutAndWithTheirPreviousName() throws Exception {
        roles.delete(PASTRY);
        roles.save(RoleConfig.of("cook", "Cook", 7));
        try {
            Document overview = page(get(ConfigSyncController.PATH));

            assertThat(overview.selectFirst("tr[data-name=user.role.cook]").attr("data-operation"))
                    .isEqualTo("DELETE");
            assertThat(overview.select("tr[data-name=user.role.cook] a.btn")).isEmpty();
            assertThat(overview.selectFirst("tr[data-name=" + RoleConfig.configName(PASTRY) + "]")
                    .attr("data-operation")).isEqualTo("CREATE");
            assertThat(page(get(ConfigSyncController.DIFF_PATH).param("name", RoleConfig.configName(PASTRY)))
                    .selectFirst("pre.config-active").text()).isEmpty();
            assertThat(page(get(ConfigSyncController.DIFF_PATH).param("name", "user.role.cook"))
                    .selectFirst("pre.config-staged").text()).isEmpty();
        } finally {
            roles.find("cook").ifPresent(role -> roles.delete(role.id()));
        }
    }

    @Test
    void aFileThatCannotBeReadIsReported() throws Exception {
        Files.writeString(directory.resolve("broken.yml"), "data: [unclosed");

        Document overview = page(get(ConfigSyncController.PATH));

        assertThat(overview.selectFirst(".config-problems li").text()).isEqualTo("broken.yml is not YAML.");
        assertThat(overview.select("table.config-changes")).isEmpty();
        Files.delete(directory.resolve("broken.yml"));
    }

    @Test
    void anImportThatWouldBeRefusedListsWhyAndDoesNotStart() throws Exception {
        roles.save(RoleConfig.of(PASTRY, "Head pastry chef", 6));
        Path file = directory.resolve(RoleConfig.configName(PASTRY) + ".yml");
        Files.writeString(file, Files.readString(file).replaceFirst("module: \\[\\]",
                "module:\n  - \"pastry\""));

        Document overview = page(get(ConfigSyncController.PATH));

        assertThat(overview.selectFirst(".config-problems li").text()).isEqualTo(RoleConfig.configName(PASTRY)
                + " depends on the module pastry, which is not enabled.");
        assertThat(overview.select("form[action$=/import]")).isEmpty();
        assertThat(send(post(ConfigSyncController.IMPORT_PATH)).getRedirectedUrl())
                .isEqualTo(ConfigSyncController.PATH);
        assertThat(roles.find(PASTRY).orElseThrow().label()).isEqualTo("Head pastry chef");
    }

    @Test
    void anImportOfAFileThatCannotBeReadDoesNotStart() throws Exception {
        Files.writeString(directory.resolve("broken.yml"), "data: [unclosed");

        assertThat(send(post(ConfigSyncController.IMPORT_PATH)).getRedirectedUrl())
                .isEqualTo(ConfigSyncController.PATH);
        Files.delete(directory.resolve("broken.yml"));
    }

    @Test
    void theSiteIsExportedToTheSyncDirectory() throws Exception {
        files.clear(directory);

        MockHttpServletResponse exported = send(post(ConfigSyncController.EXPORT_PATH));

        assertThat(exported.getRedirectedUrl()).isEqualTo(ConfigSyncController.PATH);
        assertThat(directory.resolve(RoleConfig.configName(PASTRY) + ".yml")).exists();
    }

    @Test
    void aSingleItemIsChosenAndShownAsItsExportedFile() throws Exception {
        Document empty = page(get(ConfigSyncController.SINGLE_EXPORT_PATH));
        assertThat(empty.select("option[value=" + RoleConfig.configName(PASTRY) + "]")).isNotEmpty();
        assertThat(empty.selectFirst("textarea.config-document")).isNull();

        Document shown = page(get(ConfigSyncController.SINGLE_EXPORT_PATH).param("name",
                RoleConfig.configName(PASTRY)));

        assertThat(shown.selectFirst("option[selected]").val()).isEqualTo(RoleConfig.configName(PASTRY));
        assertThat(shown.selectFirst("textarea.config-document").text()).contains("label: \"Pastry chef\"");
    }

    @Test
    void aSingleItemWithASchemaInvalidValueIsRefusedWithAClearError() throws Exception {
        assertThat(page(get(ConfigSyncController.SINGLE_IMPORT_PATH)).selectFirst("textarea[name=yaml]"))
                .isNotNull();

        Document refused = page(post(ConfigSyncController.SINGLE_IMPORT_PATH)
                .param(ConfigSyncController.NAME, RoleConfig.configName(PASTRY))
                .param(ConfigSyncController.YAML, "id: pastry_chef\nlabel: Pastry chef\nweight: heavy\n"
                        + "permissions: []\nadministrator: false\n"));

        assertThat(refused.text()).contains(RoleConfig.configName(PASTRY)
                + ": weight holds heavy, which is not a whole number.");
        assertThat(roles.find(PASTRY).orElseThrow().weight()).isEqualTo(6);
    }

    @Test
    void aSingleItemIsImportedAndReported() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MockHttpServletResponse imported = mockMvc.perform(post(ConfigSyncController.SINGLE_IMPORT_PATH)
                .param(ConfigSyncController.NAME, " " + RoleConfig.configName(PASTRY) + " ")
                .param(ConfigSyncController.YAML, "id: pastry_chef\nlabel: Head pastry chef\nweight: 6\n"
                        + "permissions: []\nadministrator: false\n")
                .session(session).with(csrf()).with(synchronizer())).andReturn().getResponse();

        assertThat(imported.getRedirectedUrl()).isEqualTo(ConfigSyncController.PATH);
        assertThat(roles.find(PASTRY).orElseThrow().label()).isEqualTo("Head pastry chef");
        Document overview = Jsoup.parse(mockMvc.perform(get(imported.getRedirectedUrl()).session(session)
                .with(synchronizer())).andReturn().getResponse().getContentAsString());
        assertThat(overview.selectFirst(".status-messages .alert-success").text())
                .isEqualTo(RoleConfig.configName(PASTRY) + " was imported.");
        assertThat(Jsoup.parse(mockMvc.perform(get(ConfigSyncController.PATH).session(session)
                .with(synchronizer())).andReturn().getResponse().getContentAsString())
                .select(".status-messages")).isEmpty();
    }

    @Test
    void aSingleItemNeedsANameOfDottedPartsAndAValue() throws Exception {
        Document refused = page(post(ConfigSyncController.SINGLE_IMPORT_PATH)
                .param(ConfigSyncController.NAME, "Not A Name").param(ConfigSyncController.YAML, ""));

        assertThat(refused.text()).contains(
                "Enter dot-separated parts of lowercase letters, digits, underscores, and hyphens.");
        assertThat(refused.select(".invalid-feedback")).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void thePageNeedsTheSynchronizePermission() throws Exception {
        assertThat(mockMvc.perform(get(ConfigSyncController.PATH).with(user("editor"))).andReturn().getResponse()
                .getStatus()).isEqualTo(403);
    }
}
