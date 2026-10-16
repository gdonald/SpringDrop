package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.security.SecurityConfig;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class AdminToolbarIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static RequestPostProcessor account(String... permissions) {
        return user(new AccountPrincipal(7L, "siteadmin", "", true, List.of(permissions)));
    }

    private Document page(String path, RequestPostProcessor who) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(who)).andReturn().getResponse().getContentAsString());
    }

    private static List<String> sections(Document page) {
        return page.select("nav.admin-toolbar [data-section]").eachAttr("data-section");
    }

    @Test
    void theToolbarShowsOnlySectionsTheUserMayAccess() throws Exception {
        Document settings = page(SiteInformationController.PATH, account(Permissions.ACCESS_TOOLBAR,
                Permissions.ACCESS_ADMINISTRATION_PAGES, Permissions.ADMINISTER_SITE_CONFIGURATION));

        assertThat(sections(settings)).containsExactly(CoreMenuLinks.CONFIGURATION);
        Element configuration = settings.selectFirst("[data-section=" + CoreMenuLinks.CONFIGURATION + "]");
        assertThat(configuration.select("[data-link]").eachAttr("data-link"))
                .contains("system.site_information", "system.performance").doesNotContain("system.admin_ban",
                        "rest.resources");
        assertThat(settings.selectFirst(".admin-toolbar-account").text()).isEqualTo("siteadmin");
        assertThat(settings.selectFirst(".admin-toolbar-logout").attr("href")).isEqualTo(SecurityConfig.LOGOUT_PATH);
        assertThat(settings.select("script[src*=bootstrap.bundle]")).hasSize(1);
    }

    @Test
    void theFirstAccountSeesEverySection() throws Exception {
        Document admin = page(AdminSectionController.PATH, user(new AccountPrincipal(UserAccount.ADMINISTRATOR_ID,
                "admin", "", true, List.of())));

        assertThat(sections(admin)).containsExactly(CoreMenuLinks.CONTENT, CoreMenuLinks.STRUCTURE,
                CoreMenuLinks.CONFIGURATION, CoreMenuLinks.EXTEND, CoreMenuLinks.PEOPLE, CoreMenuLinks.REPORTS);
        assertThat(admin.select(".admin-section [data-link]").eachAttr("data-link")).containsExactly(
                CoreMenuLinks.CONTENT, CoreMenuLinks.STRUCTURE, CoreMenuLinks.CONFIGURATION, CoreMenuLinks.EXTEND,
                CoreMenuLinks.PEOPLE, CoreMenuLinks.REPORTS);
    }

    @Test
    void aSectionWithNoPageTheUserMayOpenIsLeftOut() throws Exception {
        Document page = page(AdminSectionController.PATH, account(Permissions.ACCESS_TOOLBAR,
                Permissions.ACCESS_ADMINISTRATION_PAGES));

        assertThat(sections(page)).isEmpty();
        assertThat(page.text()).contains("You have no administrative pages here.");
    }

    @Test
    void eachSectionPageListsThePagesUnderIt() throws Exception {
        RequestPostProcessor first = user(new AccountPrincipal(UserAccount.ADMINISTRATOR_ID, "admin", "", true,
                List.of()));

        assertThat(page(AdminSectionController.STRUCTURE_PATH, first).select(".admin-section [data-link]")
                .eachAttr("data-link")).contains("block.admin_display", "system.admin_menus", "views.list");
        assertThat(page(AdminSectionController.CONFIGURATION_PATH, first).select(".admin-section [data-link]")
                .eachAttr("data-link")).contains("system.site_information", "config.sync");
        assertThat(page(AdminSectionController.REPORTS_PATH, first).select(".admin-section [data-link]")
                .eachAttr("data-link")).containsExactly("dblog.overview", "system.status");
    }

    @Test
    void someoneWithoutTheToolbarPermissionSeesNoToolbar() throws Exception {
        assertThat(page(SiteInformationController.PATH, account(Permissions.ADMINISTER_SITE_CONFIGURATION))
                .select("nav.admin-toolbar")).isEmpty();
        assertThat(Jsoup.parse(mockMvc.perform(get("/")).andReturn().getResponse().getContentAsString())
                .select("nav.admin-toolbar")).isEmpty();
    }

    @Test
    void aThemedPageCarriesTheToolbarWithoutASecondBootstrapScript() throws Exception {
        Document home = page("/", account(Permissions.ACCESS_TOOLBAR, NodePermissions.ACCESS_CONTENT));

        assertThat(home.select("nav.admin-toolbar")).hasSize(1);
        assertThat(home.select("script[src*=bootstrap.bundle]")).hasSize(1);
    }

    @Test
    void signingOutAsksFirst() throws Exception {
        Document confirm = page(SecurityConfig.LOGOUT_PATH, account());

        assertThat(confirm.selectFirst("form[action=" + SecurityConfig.LOGOUT_PATH + "] button").text())
                .isEqualTo("Sign out");
    }
}
