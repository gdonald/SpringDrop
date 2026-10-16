package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.block.BlockPlacement;
import dev.springdrop.kernel.block.BlockPlacementManager;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.theme.BigPipe;
import dev.springdrop.kernel.theme.Theme;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class ContextualLinksIntegrationTest extends AbstractIntegrationTest {

    private static final String NOTICE = "front_end_contextual_notice";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BlockPlacementManager blocks;

    @Autowired
    private NodeTypeManager types;

    @Autowired
    private NodeService nodes;

    @Autowired
    private EntityQueryExecutor queries;

    @BeforeEach
    void aBlockAndAContentType() {
        types.save(NodeType.of("page", "Basic page"));
        blocks.save(new BlockPlacement(NOTICE, Theme.FRONT_END, "footer", "custom_html", "Notice", false, 0,
                Map.of("body", "<p>Opening hours change on Monday.</p>"), List.of()));
    }

    @AfterEach
    void nothingLeft() {
        blocks.delete(NOTICE);
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        types.delete("page");
    }

    private static RequestPostProcessor account(long id, String... permissions) {
        return user(new AccountPrincipal(id, "account" + id, "", true, List.of(permissions)));
    }

    /** The page drawn whole, as for a browser without JavaScript, so its placeholders are filled in place. */
    private Document page(String path, RequestPostProcessor who) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(who).cookie(new Cookie(BigPipe.NO_JS_COOKIE, "1")))
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void aPermittedUserSeesAContextualConfigureLinkOnABlock() throws Exception {
        Document home = page("/", account(7L, NodePermissions.ACCESS_CONTENT, Permissions.ADMINISTER_BLOCKS));

        var links = home.select("[data-contextual=block:" + NOTICE + "] a");
        assertThat(links.eachText()).containsExactly("Configure block");
        assertThat(links.first().attr("href")).isEqualTo(BlockLayoutController.PATH + "/manage/" + NOTICE);
        assertThat(links.first().classNames()).contains("btn", "btn-secondary", "btn-sm");

        assertThat(page("/", account(8L, NodePermissions.ACCESS_CONTENT)).select(".contextual-links")).isEmpty();
    }

    @Test
    void contentCarriesEditAndDeleteLinksForThoseWhoMayChangeIt() throws Exception {
        long hours = ((Number) nodes.save(EntityData.of(NodeEntityType.ID, null, "page", "Opening hours",
                Map.of(BaseFieldDefinition.STATUS, true, BaseFieldDefinition.OWNER, 7L)), 7L).id()).longValue();

        Document owner = page(NodeEntityType.path(hours), account(7L, NodePermissions.ACCESS_CONTENT,
                NodePermissions.editOwn("page")));
        assertThat(owner.select("[data-contextual=node:" + hours + "] a").eachText()).containsExactly("Edit");
        Document administrator = page(NodeEntityType.path(hours), account(9L, NodePermissions.ACCESS_CONTENT,
                NodePermissions.BYPASS_NODE_ACCESS));
        assertThat(administrator.select("[data-contextual=node:" + hours + "] a").eachText())
                .containsExactly("Edit", "Delete");
        assertThat(page(NodeEntityType.path(hours), account(8L, NodePermissions.ACCESS_CONTENT))
                .select(".contextual-links")).isEmpty();
    }

    @Test
    void aSaveActionShowsASuccessMessageOnce() throws Exception {
        MockHttpSession session = new MockHttpSession();
        RequestPostProcessor editor = account(7L, NodePermissions.ACCESS_CONTENT, NodePermissions.create("page"),
                NodePermissions.editOwn("page"));

        MockHttpServletResponse saved = mockMvc.perform(post(NodeController.ADD_PATH + "/page").session(session)
                .with(editor).with(csrf()).param(NodeController.TITLE, "Opening hours")).andReturn().getResponse();
        Document shown = Jsoup.parse(mockMvc.perform(get(saved.getRedirectedUrl()).session(session).with(editor))
                .andReturn().getResponse().getContentAsString());

        assertThat(shown.selectFirst(".alert-success").text()).isEqualTo("Basic page Opening hours has been created.");
        assertThat(Jsoup.parse(mockMvc.perform(get(saved.getRedirectedUrl()).session(session).with(editor))
                .andReturn().getResponse().getContentAsString()).select(".alert-success")).isEmpty();
    }

    @Test
    void deletingContentSaysSo() throws Exception {
        long hours = ((Number) nodes.save(EntityData.of(NodeEntityType.ID, null, "page", "Opening hours",
                Map.of(BaseFieldDefinition.STATUS, true, BaseFieldDefinition.OWNER, 9L)), 9L).id()).longValue();
        MockHttpSession session = new MockHttpSession();
        RequestPostProcessor administrator = account(9L, NodePermissions.ACCESS_CONTENT,
                NodePermissions.BYPASS_NODE_ACCESS);

        mockMvc.perform(post(NodeController.deletePath(hours)).session(session).with(administrator).with(csrf()));
        Document home = Jsoup.parse(mockMvc.perform(get("/").session(session)
                .with(administrator)).andReturn().getResponse().getContentAsString());

        assertThat(home.selectFirst(".alert-success").text()).isEqualTo("Basic page Opening hours has been deleted.");
    }

    @Test
    void aSettingsSaveShowsItsMessageOnTheFormItReturnsTo() throws Exception {
        MockHttpSession session = new MockHttpSession();
        RequestPostProcessor administrator = account(9L, Permissions.ADMINISTER_SITE_CONFIGURATION);

        mockMvc.perform(post(RssSettingsController.PATH).session(session).with(administrator).with(csrf())
                .param(RssSettingsController.ITEMS, "10"));
        Document form = Jsoup.parse(mockMvc.perform(get(RssSettingsController.PATH).session(session)
                .with(administrator)).andReturn().getResponse().getContentAsString());

        assertThat(form.selectFirst(".status-messages .alert-success").text())
                .isEqualTo("The configuration options have been saved.");
    }
}
