package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dev.springdrop.kernel.image.ImageStyleManager;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.views.DefaultViews;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class AdminListingIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ImageStyleManager imageStyles;

    @Autowired
    private DefaultViews defaultViews;

    private static final RequestPostProcessor FIRST = user(new AccountPrincipal(UserAccount.ADMINISTRATOR_ID, "admin",
            "", true, List.of()));

    @BeforeEach
    void defaults() {
        imageStyles.installDefaults();
        defaultViews.install();
    }

    private Document page(String path) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(FIRST)).andReturn().getResponse().getContentAsString());
    }

    @Test
    void anOverviewDrawsTheSharedTableRowActionsAndLocalAction() throws Exception {
        Document styles = page(ImageStyleController.PATH);

        assertThat(styles.select("table.admin-listing")).hasSize(1);
        assertThat(styles.select("[data-row-action=edit]")).isNotEmpty()
                .allSatisfy(edit -> assertThat(edit.classNames()).contains("btn", "btn-secondary", "btn-sm"));
        assertThat(styles.selectFirst("[data-local-action]").text()).isEqualTo("Add image style");
        BootstrapAssertions.assertNoOutlineButtons(styles);
        BootstrapAssertions.assertEditControlsAreButtons(styles);
    }

    @Test
    void theContentListingsShareATabBar() throws Exception {
        Document comments = page(CommentAdminController.PATH);
        assertThat(comments.select(".admin-local-tasks .nav-link").eachText())
                .containsExactly("Content", "Comments", "Media", "Blocks");
        assertThat(comments.selectFirst(".admin-local-tasks .nav-link.active").text()).isEqualTo("Comments");

        Document content = page("/admin/content");
        assertThat(content.select(".nav-tabs .nav-link.active").text()).isEqualTo("Content");
        assertThat(content.select(".btn-primary").eachText()).contains("Add content");
    }

    @Test
    void thePeoplePagesShareATabBar() throws Exception {
        assertThat(page(RolePermissionsController.PATH).select(".admin-local-tasks .nav-link").eachText())
                .containsExactly("People", "Permissions");
    }

    @Test
    void anOverviewWithNothingToListSaysSo() throws Exception {
        Document redirects = page(RedirectController.PATH);

        assertThat(redirects.select(".admin-listing-empty").isEmpty())
                .isEqualTo(!redirects.select("table.admin-listing tbody tr").isEmpty());
    }
}
