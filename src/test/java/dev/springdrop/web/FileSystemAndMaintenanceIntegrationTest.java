package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.file.FileSchemes;
import dev.springdrop.kernel.file.FileSystemSettings;
import dev.springdrop.kernel.file.UploadSchemes;
import dev.springdrop.kernel.maintenance.Maintenance;
import dev.springdrop.kernel.maintenance.MaintenanceSettings;
import dev.springdrop.kernel.maintenance.PermissionMaintenanceBypass;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.security.SecurityConfig;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class FileSystemAndMaintenanceIntegrationTest extends AbstractIntegrationTest {

    private static final String MESSAGE = "Back after the database upgrade at 02:00.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConfigStore config;

    @Autowired
    private UploadSchemes uploadSchemes;

    @Autowired
    private Maintenance maintenance;

    @AfterEach
    void defaults() {
        config.delete(FileSystemSettings.CONFIG_NAME);
        config.delete(MaintenanceSettings.CONFIG_NAME);
    }

    private static RequestPostProcessor administrator() {
        return user(new AccountPrincipal(5L, "admin", "", true, List.of(Permissions.ADMINISTER_SITE_CONFIGURATION,
                PermissionMaintenanceBypass.ACCESS_SITE_IN_MAINTENANCE_MODE)));
    }

    private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(csrf()).with(administrator())).andReturn().getResponse();
    }

    private Document page(MockHttpServletRequestBuilder request) throws Exception {
        return Jsoup.parse(send(request).getContentAsString());
    }

    private MockHttpServletResponse visit(String path, RequestPostProcessor... who) throws Exception {
        MockHttpServletRequestBuilder request = get(path);
        for (RequestPostProcessor each : who) {
            request.with(each);
        }
        return mockMvc.perform(request).andReturn().getResponse();
    }

    @Test
    void theMaintenanceToggleDrivesTheGate() throws Exception {
        assertThat(visit("/").getStatus()).isEqualTo(200);

        MockHttpServletResponse saved = send(post(MaintenanceController.PATH)
                .param(MaintenanceController.MODE, "true").param(MaintenanceController.MESSAGE, MESSAGE));
        assertThat(saved.getRedirectedUrl()).isEqualTo(MaintenanceController.PATH);

        MockHttpServletResponse visitor = visit("/");
        assertThat(visitor.getStatus()).isEqualTo(503);
        assertThat(visitor.getContentAsString()).contains(MESSAGE);
        assertThat(visit("/", user(new AccountPrincipal(7L, "reader", "", true, List.of()))).getStatus())
                .isEqualTo(503);
        assertThat(visit("/", administrator()).getStatus()).isEqualTo(200);
        assertThat(visit("/", user(new AccountPrincipal(UserAccount.ADMINISTRATOR_ID, "first", "", true,
                List.of()))).getStatus()).isEqualTo(200);
        assertThat(visit(SecurityConfig.LOGIN_PATH).getStatus()).isEqualTo(200);

        send(post(MaintenanceController.PATH).param(MaintenanceController.MESSAGE, MESSAGE));

        assertThat(visit("/").getStatus()).isEqualTo(200);
        assertThat(maintenance.settings()).isEqualTo(new MaintenanceSettings(false, MESSAGE));
    }

    @Test
    void theMaintenanceFormShowsTheSettingsAndNeedsAMessageWhenTurnedOn() throws Exception {
        Document form = page(get(MaintenanceController.PATH));
        assertThat(form.selectFirst("input[name=maintenance_mode]").hasAttr("checked")).isFalse();
        assertThat(form.selectFirst("textarea[name=maintenance_mode_message]").text()).isNotBlank();

        Document refused = page(post(MaintenanceController.PATH).param(MaintenanceController.MODE, "true")
                .param(MaintenanceController.MESSAGE, " "));

        assertThat(refused.select(".invalid-feedback")).isNotEmpty();
        assertThat(maintenance.settings().enabled()).isFalse();
        maintenance.save(new MaintenanceSettings(true, MESSAGE));
        assertThat(page(get(MaintenanceController.PATH))
                .selectFirst("input[name=maintenance_mode]").hasAttr("checked")).isTrue();
    }

    @Test
    void theFileSystemFormShowsThePathsAndTheDownloadMethod() throws Exception {
        Document form = page(get(FileSystemController.PATH));
        assertThat(form.selectFirst("input[name=public_path]").hasAttr("readonly")).isTrue();
        assertThat(form.selectFirst("input[name=private_path]").val()).endsWith("private");
        assertThat(form.selectFirst("input[name=temporary_path]").val()).isNotBlank();
        assertThat(form.selectFirst("input[name=file_default_scheme][value=public]").hasAttr("checked")).isTrue();
    }

    @Test
    void aDownloadMethodTheFormDoesNotOfferIsRefused() throws Exception {
        Document refused = page(post(FileSystemController.PATH).param(FileSystemController.DEFAULT_SCHEME, "s3"));

        assertThat(refused.text()).contains("Choose a download method.");
        assertThat(uploadSchemes.defaultScheme()).isEqualTo(FileSchemes.PUBLIC);
    }

    @Test
    void aFieldThatNamesItsSchemeKeepsIt() {
        config.save(FileSystemSettings.CONFIG_NAME, new FileSystemSettings(FileSchemes.PRIVATE));

        assertThat(uploadSchemes.schemeFor(Map.of("uri_scheme", FileSchemes.PUBLIC))).isEqualTo(FileSchemes.PUBLIC);
        assertThat(uploadSchemes.schemeFor(Map.of())).isEqualTo(FileSchemes.PRIVATE);
    }

    @Test
    void theFormsNeedTheSiteConfigurationPermission() throws Exception {
        RequestPostProcessor editor = user("editor").authorities(new SimpleGrantedAuthority("access content"));

        assertThat(visit(FileSystemController.PATH, editor).getStatus()).isEqualTo(403);
        assertThat(visit(MaintenanceController.PATH, editor).getStatus()).isEqualTo(403);
    }
}
