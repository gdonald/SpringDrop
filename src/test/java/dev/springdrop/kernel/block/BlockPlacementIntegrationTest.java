package dev.springdrop.kernel.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.block.conditions.UserRoleCondition;
import dev.springdrop.kernel.block.plugins.CustomHtmlBlock;
import dev.springdrop.kernel.block.plugins.MainContentBlock;
import dev.springdrop.kernel.block.plugins.MenuBlockDeriver;
import dev.springdrop.kernel.block.plugins.PageTitleBlock;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.menu.MenuManager;
import dev.springdrop.kernel.render.RenderedPage;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.theme.PageChrome;
import dev.springdrop.kernel.theme.Region;
import dev.springdrop.kernel.theme.Theme;
import dev.springdrop.support.AbstractIntegrationTest;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class BlockPlacementIntegrationTest extends AbstractIntegrationTest {

    private static final String SIDEBAR = "sidebar";

    private static final String EDITOR = "editor";

    private static final BlockContext FRONT_PAGE = BlockContext.of("/", "Home");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BlockPlacementManager placements;

    @Autowired
    private BlockRegionBuilder regions;

    @Autowired
    private BlockPageRenderer pages;

    @Autowired
    private MenuManager menus;

    @Autowired
    private MenuLinkContentService storedLinks;

    @BeforeEach
    void noBlocksPlaced() {
        menus.install();
        storedLinks.install();
        placements.all().forEach(placement -> placements.delete(placement.id()));
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void noBlocksLeftBehind() {
        placements.all().forEach(placement -> placements.delete(placement.id()));
        SecurityContextHolder.clearContext();
    }

    private static BlockPlacement notice(String id, String region, String body, int weight) {
        return BlockPlacement.of(id, Theme.FRONT_END, region, CustomHtmlBlock.ID, id)
                .withSettings(Map.of(CustomHtmlBlock.BODY, "<p>" + body + "</p>"))
                .withWeight(weight);
    }

    private static ConditionConfig holding(String role) {
        return ConditionConfig.of(UserRoleCondition.ID, Map.of(UserRoleCondition.ROLES, List.of(role)));
    }

    private Document frontPage(RequestPostProcessor visitor) throws Exception {
        return Jsoup.parse(mockMvc.perform(get("/").with(visitor))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private Document region(String region, BlockContext context) {
        return Jsoup.parseBodyFragment(pages.render(PageChrome.of("SpringDrop", "Home"),
                Renderable.of("text").with("value", "Welcome"), context).html())
                .select(".region-" + region).stream().findFirst()
                .map(element -> Jsoup.parseBodyFragment(element.outerHtml()))
                .orElse(Jsoup.parseBodyFragment(""));
    }

    @Test
    void aBlockWithARoleConditionShowsOnlyToThatRole() throws Exception {
        placements.save(notice("staff_notice", SIDEBAR, "Staff meeting at noon", 0)
                .withVisibility(List.of(holding(EDITOR))));

        assertThat(frontPage(user("edith").roles(EDITOR)).select(".region-sidebar p").text())
                .isEqualTo("Staff meeting at noon");
        assertThat(frontPage(user("walter").roles("reader")).select(".region-sidebar")).isEmpty();
        assertThat(frontPage(anonymousVisitor()).select(".region-sidebar")).isEmpty();
    }

    @Test
    void blocksInARegionComeOutLightestFirst() throws Exception {
        placements.save(notice("heavy", SIDEBAR, "Third", 10));
        placements.save(notice("light", SIDEBAR, "First", -5));
        placements.save(notice("middle", SIDEBAR, "Second", 0));

        assertThat(frontPage(anonymousVisitor()).select(".region-sidebar p"))
                .extracting(element -> element.text())
                .containsExactly("First", "Second", "Third");
    }

    @Test
    void reweightingABlockMovesItWithinItsRegion() throws Exception {
        placements.save(notice("first", SIDEBAR, "Agenda", 0));
        placements.save(notice("second", SIDEBAR, "Minutes", 5));

        placements.save(placements.find("second").orElseThrow().withWeight(-5));

        assertThat(frontPage(anonymousVisitor()).select(".region-sidebar p"))
                .extracting(element -> element.text())
                .containsExactly("Minutes", "Agenda");
    }

    @Test
    void aPlacementRoundTripsEverythingItWasGiven() {
        BlockPlacement placed = notice("notice", SIDEBAR, "Closed Monday", 3)
                .withoutLabel()
                .withVisibility(List.of(holding(EDITOR).negated()));

        placements.save(placed);

        assertThat(placements.find("notice")).contains(placed);
    }

    @Test
    void aDeletedPlacementIsGone() {
        placements.save(notice("notice", SIDEBAR, "Closed Monday", 0));

        placements.delete("notice");

        assertThat(placements.find("notice")).isEmpty();
    }

    @Test
    void aThemesPlacementsComeInRegionThenWeightThenLabelOrder() {
        placements.save(notice("b_second", SIDEBAR, "x", 0));
        placements.save(notice("a_first", SIDEBAR, "x", 0));
        placements.save(notice("heavy", SIDEBAR, "x", 9));
        placements.save(notice("content_block", Region.CONTENT, "x", 50));
        placements.save(BlockPlacement.of("admin_block", Theme.ADMIN, SIDEBAR, CustomHtmlBlock.ID, "Admin"));

        assertThat(placements.inTheme(Theme.FRONT_END)).extracting(BlockPlacement::id)
                .containsExactly("content_block", "a_first", "b_second", "heavy");
    }

    @Test
    void aBlockIsDrawnInsideTheBlockTemplateWithItsTitle() {
        placements.save(notice("notice", SIDEBAR, "Closed Monday", 0));

        Document sidebar = region(SIDEBAR, FRONT_PAGE);

        assertThat(sidebar.selectFirst("#block-notice").attr("data-block-plugin")).isEqualTo(CustomHtmlBlock.ID);
        assertThat(sidebar.selectFirst("#block-notice h2").text()).isEqualTo("notice");
        assertThat(sidebar.selectFirst("#block-notice .block-content p").text()).isEqualTo("Closed Monday");
    }

    @Test
    void aBlockWhoseTitleIsHiddenIsDrawnWithoutOne() {
        placements.save(notice("notice", SIDEBAR, "Closed Monday", 0).withoutLabel());

        assertThat(region(SIDEBAR, FRONT_PAGE).select("#block-notice h2")).isEmpty();
    }

    @Test
    void aPlacementInARegionTheThemeLacksIsNotDrawn() {
        placements.save(notice("lost", "attic", "Nowhere", 0));

        assertThat(regions.build(Theme.FRONT_END, FRONT_PAGE).regions()).isEmpty();
    }

    @Test
    void aPlacementOfABlockTheSiteNoLongerHasIsNotDrawn() {
        placements.save(BlockPlacement.of("gone", Theme.FRONT_END, SIDEBAR, "vanished_block", "Gone"));

        assertThat(regions.build(Theme.FRONT_END, FRONT_PAGE).regions()).isEmpty();
    }

    @Test
    void aBlockWithNothingToShowLeavesItsRegionOffThePage() {
        placements.save(BlockPlacement.of("title", Theme.FRONT_END, SIDEBAR, PageTitleBlock.ID, "Title"));

        assertThat(regions.build(Theme.FRONT_END, BlockContext.of("/", "")).regions()).isEmpty();
    }

    @Test
    void whatTheConditionsReadIsCarriedEvenWhenTheBlockIsHidden() {
        placements.save(notice("staff_notice", SIDEBAR, "Staff only", 0).withVisibility(List.of(holding(EDITOR))));

        PageRegions built = regions.build(Theme.FRONT_END, FRONT_PAGE);

        assertThat(built.regions()).isEmpty();
        assertThat(built.cacheability().contexts()).contains(UserRoleCondition.USER_ROLES_CONTEXT);
        assertThat(built.cacheability().tags()).contains(BlockPlacement.LIST_CACHE_TAG);
    }

    @Test
    void aDrawnBlockCarriesItsPlacementsTagAndItsPluginsCacheability() {
        placements.save(BlockPlacement.of("main_menu", Theme.FRONT_END, SIDEBAR, MenuBlockDeriver.PRIMARY, "Menu"));

        RenderedPage page = pages.render(PageChrome.of("SpringDrop", "Home"),
                Renderable.of("text").with("value", "Welcome"), FRONT_PAGE);

        assertThat(page.cache().tags()).contains("config:block.block.main_menu", "menu:main");
    }

    @Test
    void theMainContentBlockDrawsThePageContentInItsRegionAndNowhereElse() {
        placements.save(BlockPlacement.of("main", Theme.FRONT_END, Region.CONTENT, MainContentBlock.ID, "Main")
                .withoutLabel());

        Document page = Jsoup.parse(pages.render(PageChrome.of("SpringDrop", "Home"),
                Renderable.of("text").with("value", "Welcome").attribute("class", "welcome"), FRONT_PAGE).html());

        assertThat(page.select(".welcome")).hasSize(1);
        assertThat(page.selectFirst(".region-content .welcome")).isNotNull();
    }

    @Test
    void withNoMainContentBlockPlacedTheLayoutDrawsTheContentItself() {
        PageRegions built = regions.build(Theme.FRONT_END, FRONT_PAGE.withMainContent(Renderable.of("text")));

        Document page = Jsoup.parse(pages.render(PageChrome.of("SpringDrop", "Home"),
                Renderable.of("text").with("value", "Welcome").attribute("class", "welcome"), FRONT_PAGE).html());

        assertThat(built.mainContentPlaced()).isFalse();
        assertThat(page.select("main .col-lg-8 .welcome")).hasSize(1);
    }

    @Test
    void aMainContentBlockOnAPageWithNoContentDoesNotCountAsPlaced() {
        placements.save(BlockPlacement.of("main", Theme.FRONT_END, Region.CONTENT, MainContentBlock.ID, "Main"));

        assertThat(regions.build(Theme.FRONT_END, FRONT_PAGE).mainContentPlaced()).isFalse();
    }

    @Test
    void aBlockIsDrawnByTheMostSpecificTemplateItsPlacementSuggests() {
        BlockPlacement placement = BlockPlacement.of(
                "main_menu", Theme.FRONT_END, SIDEBAR, MenuBlockDeriver.PRIMARY, "Menu");

        assertThat(BlockRegionBuilder.suggestions(placement)).containsExactly(
                "block--main_menu", "block--system_menu_block--main", "block--system_menu_block", "block");
    }

    private static RequestPostProcessor anonymousVisitor() {
        return request -> request;
    }
}
