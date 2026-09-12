package dev.springdrop.kernel.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.web.TokenBrowserController;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class AdminThemeTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ThemeRegistry registry;

    private static RequestPostProcessor administrator() {
        return user("admin")
                .authorities(new SimpleGrantedAuthority(Permissions.ADMINISTER_SITE_CONFIGURATION));
    }

    @Test
    void anAdminRouteRendersUnderTheAdminTheme() throws Exception {
        Document page = getPage(TokenBrowserController.PATH);

        assertThat(page.selectFirst("#admin-toolbar")).isNotNull();
        assertThat(page.selectFirst("body").hasClass("admin-page")).isTrue();
    }

    @Test
    void aFrontEndRouteRendersUnderTheFrontEndTheme() throws Exception {
        Document page = getPage("/");

        assertThat(page.selectFirst("#admin-toolbar")).isNull();
        assertThat(page.selectFirst("nav.navbar")).isNotNull();
        assertThat(page.selectFirst("main.container")).isNotNull();
    }

    @Test
    void theAdminLayoutRunsItsTablesTighterThanTheFrontEndDoes() throws Exception {
        assertThat(getPage(TokenBrowserController.PATH).selectFirst("style").data())
                .contains(".admin-page .table");
        assertThat(getPage("/").select("style")).isEmpty();
    }

    @Test
    void theAdminLayoutGivesItsContentTheFullWidth() throws Exception {
        Document page = getPage(TokenBrowserController.PATH);

        assertThat(page.selectFirst("main.container-fluid")).isNotNull();
        assertThat(page.selectFirst("main .row > .col-12")).isNotNull();
    }

    @Test
    void theAdminThemeDrawsThePartialsItInheritsFromTheBaseTheme() throws Exception {
        Document page = getPage(TokenBrowserController.PATH);

        assertThat(page.select(".breadcrumb .breadcrumb-item"))
                .extracting(element -> element.text())
                .containsExactly("Home", "Administration");
    }

    @Test
    void theAdminThemeInheritsTheBaseThemeBeforeTheCoreTemplates() {
        assertThat(registry.chainFor(Theme.ADMIN))
                .containsExactly("themes/admin", "themes/front-end", "");
    }

    @Test
    void aRequestLeavesTheSiteDefaultInChargeOnceItIsDone() throws Exception {
        getPage(TokenBrowserController.PATH);

        assertThat(registry.active()).contains(Theme.FRONT_END);
    }

    private Document getPage(String path) throws Exception {
        String html = mockMvc.perform(get(path).with(administrator()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return Jsoup.parse(html);
    }
}
