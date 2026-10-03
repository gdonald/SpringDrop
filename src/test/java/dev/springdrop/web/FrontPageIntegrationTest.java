package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.menu.MenuManager;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.LinkedHashMap;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class FrontPageIntegrationTest extends AbstractIntegrationTest {

    /** How many teasers the front page view shows a page. */
    private static final int FRONT_PAGE_SIZE = 10;

    private static final String ARTICLE = "article";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NodeService nodes;

    @Autowired
    private NodeTypeManager types;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private ConfigStore configStore;

    @Autowired
    private MenuManager menus;

    @Autowired
    private MenuLinkContentService storedLinks;

    private SiteInformation siteAsItWas;

    @BeforeEach
    void anArticleType() {
        menus.install();
        storedLinks.install();
        clearContent();
        types.save(NodeType.of(ARTICLE, "Article"));
        siteAsItWas = configStore.read(SiteInformation.CONFIG_NAME, SiteInformation.class, null);
    }

    @AfterEach
    void theSiteAsItWas() {
        clearContent();
        if (siteAsItWas == null) {
            configStore.delete(SiteInformation.CONFIG_NAME);
        } else {
            configStore.save(SiteInformation.CONFIG_NAME, siteAsItWas);
        }
    }

    private void clearContent() {
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        types.all().forEach(type -> types.delete(type.id()));
    }

    private static RequestPostProcessor reader() {
        return user("reader").authorities(new SimpleGrantedAuthority(NodePermissions.ACCESS_CONTENT));
    }

    private EntityData written(String title, boolean published, boolean promoted, boolean sticky) {
        Map<String, Object> values = new LinkedHashMap<>(nodes.create(types.find(ARTICLE).orElseThrow(), 7L).fields());
        values.put(BaseFieldDefinition.STATUS, published);
        values.put(NodeEntityType.PROMOTE, promoted);
        values.put(NodeEntityType.STICKY, sticky);
        return nodes.save(EntityData.of(NodeEntityType.ID, null, ARTICLE, title, values), 7L);
    }

    private Document page(String path, RequestPostProcessor who) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(who))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static List<String> teaserTitles(Document document) {
        return document.select("article.node--teaser h2").eachText();
    }

    @Test
    void aPromotedStickyNodeComesFirstThenTheNewestPromotedNodes() throws Exception {
        written("Older sticky notice", true, true, true);
        written("Spring schedule", true, true, false);
        written("Summer schedule", true, true, false);
        written("Not promoted", true, false, false);
        written("Unpublished draft", false, true, true);

        assertThat(teaserTitles(page(HomeController.PATH, reader())))
                .containsExactly("Older sticky notice", "Summer schedule", "Spring schedule");
    }

    @Test
    void theListingIsAlsoAtItsOwnPath() throws Exception {
        written("Spring schedule", true, true, false);

        assertThat(teaserTitles(page(HomeController.LISTING_PATH, reader()))).containsExactly("Spring schedule");
    }

    @Test
    void theListingShowsTenNodesAPageWithAPager() throws Exception {
        for (int number = 1; number <= FRONT_PAGE_SIZE + 1; number++) {
            written("Notice " + number, true, true, false);
        }

        Document first = page(HomeController.PATH, reader());
        Document second = page(HomeController.PATH + "?page=2", reader());

        assertThat(teaserTitles(first)).hasSize(FRONT_PAGE_SIZE).first().isEqualTo("Notice 11");
        assertThat(teaserTitles(second)).containsExactly("Notice 1");
        assertThat(first.select("a[href=/?page=2]")).isNotEmpty();
    }

    @Test
    void aPageNumberOutOfRangeOrNotANumberReadsAsTheNearestPage() throws Exception {
        written("Spring schedule", true, true, false);

        assertThat(teaserTitles(page(HomeController.PATH + "?page=9", reader()))).containsExactly("Spring schedule");
        assertThat(teaserTitles(page(HomeController.PATH + "?page=0", reader()))).containsExactly("Spring schedule");
        assertThat(teaserTitles(page(HomeController.PATH + "?page=last", reader())))
                .containsExactly("Spring schedule");
    }

    @Test
    void aListingWithNothingInItSaysSo() throws Exception {
        assertThat(page(HomeController.PATH, reader()).text())
                .contains("No front page content has been created yet.");
    }

    @Test
    void someoneWhoMayNotReadContentSeesNoneOfIt() throws Exception {
        written("Spring schedule", true, true, false);

        Document document = page(HomeController.PATH, user("visitor"));

        assertThat(teaserTitles(document)).isEmpty();
        assertThat(document.text()).contains("No front page content has been created yet.");
    }

    @Test
    void theHomeShowsThePageTheSiteNamesAsItsFrontPage() throws Exception {
        EntityData node = written("About us", true, false, false);
        configStore.save(SiteInformation.CONFIG_NAME,
                SiteInformation.DEFAULTS.withFrontPage(NodeEntityType.path(node.id())));

        mockMvc.perform(get(HomeController.PATH).with(reader()))
                .andExpect(forwardedUrl(NodeEntityType.path(node.id())));
    }

    @Test
    void aFrontPageLeftBlankOrSetToTheHomeIsTheListing() {
        assertThat(SiteInformation.DEFAULTS.withFrontPage("").frontPage()).isEqualTo(HomeController.LISTING_PATH);
        assertThat(SiteInformation.DEFAULTS.withFrontPage("/").frontPage()).isEqualTo(HomeController.LISTING_PATH);
        assertThat(SiteInformation.DEFAULTS.withFrontPage(null).frontPage()).isEqualTo(HomeController.LISTING_PATH);
    }
}
