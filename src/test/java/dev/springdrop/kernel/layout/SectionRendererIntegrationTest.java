package dev.springdrop.kernel.layout;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockInstance;
import dev.springdrop.kernel.block.plugins.CustomHtmlBlock;
import dev.springdrop.kernel.block.plugins.PageTitleBlock;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.layout.layouts.OneColumnLayout;
import dev.springdrop.kernel.layout.layouts.ThreeColumnLayout;
import dev.springdrop.kernel.layout.layouts.TwoColumnLayout;
import dev.springdrop.kernel.render.RenderService;
import dev.springdrop.kernel.render.RenderedPage;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SectionRendererIntegrationTest extends AbstractIntegrationTest {

    private static final BlockContext PAGE = BlockContext.of("/trips", "Trips");

    private static final String SAVED_SECTIONS = "layout_test.sections";

    @Autowired
    private LayoutManager layouts;

    @Autowired
    private SectionRenderer sections;

    @Autowired
    private RenderService renderer;

    @Autowired
    private ConfigStore configStore;

    private static SectionComponent html(String id, String region, int weight, String body) {
        BlockInstance block = BlockInstance.of(id, CustomHtmlBlock.ID, "Block " + id)
                .withSettings(Map.of(CustomHtmlBlock.BODY, "<p>" + body + "</p>"));
        return new SectionComponent(region, weight, block);
    }

    private RenderedPage rendered(List<Section> layout) {
        return renderer.render(sections.render(layout, PAGE));
    }

    private Document page(Section... layout) {
        return Jsoup.parseBodyFragment(rendered(List.of(layout)).html());
    }

    private static Element region(Element section, String region) {
        return section.selectFirst("[data-region=" + region + "]");
    }

    private static List<String> columnClasses(Document page) {
        return page.select(".layout__region").stream()
                .map(column -> column.className().replace("layout__region col-12 ", ""))
                .toList();
    }

    @Test
    void theLibraryListsTheOneTwoAndThreeColumnLayoutsByLabel() {
        assertThat(layouts.definitions()).containsSubsequence(
                new LayoutDefinition(OneColumnLayout.ID, "One column"),
                new LayoutDefinition(ThreeColumnLayout.ID, "Three column"),
                new LayoutDefinition(TwoColumnLayout.ID, "Two column"));
    }

    @Test
    void aLayoutWithTwoSectionsRendersEachBlockIntoItsRegion() {
        Section intro = Section.of(OneColumnLayout.ID)
                .withComponent(html("intro", OneColumnLayout.CONTENT, 0, "Spring schedule"));
        Section columns = Section.of(TwoColumnLayout.ID)
                .withComponent(html("routes", TwoColumnLayout.SECOND, 0, "Route list"))
                .withComponent(html("fares", TwoColumnLayout.FIRST, 0, "Fare table"));

        List<Element> drawn = page(intro, columns).select(".layout");

        assertThat(drawn).extracting(section -> section.attr("data-layout"))
                .containsExactly(OneColumnLayout.ID, TwoColumnLayout.ID);
        assertThat(region(drawn.get(0), OneColumnLayout.CONTENT).text()).isEqualTo("Block intro Spring schedule");
        assertThat(region(drawn.get(1), TwoColumnLayout.FIRST).text()).isEqualTo("Block fares Fare table");
        assertThat(region(drawn.get(1), TwoColumnLayout.SECOND).text()).isEqualTo("Block routes Route list");
    }

    @Test
    void blocksInOneRegionAreDrawnLightestFirst() {
        Section section = Section.of(OneColumnLayout.ID)
                .withComponent(html("later", OneColumnLayout.CONTENT, 5, "Later"))
                .withComponent(html("sooner", OneColumnLayout.CONTENT, -2, "Sooner"));

        assertThat(page(section).select(".block").eachAttr("id")).containsExactly("block-sooner", "block-later");
    }

    @Test
    void aBlockWithItsLabelHiddenShowsOnlyItsContent() {
        SectionComponent unlabeled = html("notice", OneColumnLayout.CONTENT, 0, "Office closed");
        unlabeled = new SectionComponent(unlabeled.region(), unlabeled.weight(), unlabeled.block().withoutLabel());

        assertThat(page(Section.of(OneColumnLayout.ID).withComponent(unlabeled)).select(".layout").text())
                .isEqualTo("Office closed");
    }

    @Test
    void chosenColumnWidthsSetEachRegionsShareOfTheGrid() {
        Section section = Section.of(TwoColumnLayout.ID).withColumnWidths("33-67");

        Document page = page(section);

        assertThat(page.selectFirst(".layout").attr("data-column-widths")).isEqualTo("33-67");
        assertThat(columnClasses(page)).containsExactly("col-md-4", "col-md-8");
    }

    @Test
    void threeColumnWidthsSplitTheGridAcrossAllThreeRegions() {
        assertThat(columnClasses(page(Section.of(ThreeColumnLayout.ID).withColumnWidths("25-50-25"))))
                .containsExactly("col-md-3", "col-md-6", "col-md-3");
    }

    @Test
    void aSectionWithNoWidthsChosenUsesTheLayoutsFirst() {
        assertThat(columnClasses(page(Section.of(ThreeColumnLayout.ID)))).containsExactly(
                "col-md-4", "col-md-4", "col-md-4");
    }

    @Test
    void widthsTheLayoutDoesNotOfferFallBackToItsFirst() {
        Document page = page(Section.of(TwoColumnLayout.ID).withColumnWidths("10-90"));

        assertThat(page.selectFirst(".layout").attr("data-column-widths")).isEqualTo("50-50");
        assertThat(columnClasses(page)).containsExactly("col-md-6", "col-md-6");
    }

    @Test
    void theOneColumnLayoutSpansTheWholeRow() {
        assertThat(columnClasses(page(Section.of(OneColumnLayout.ID)))).containsExactly("col-md-12");
    }

    @Test
    void anEmptyRegionStillHoldsItsColumn() {
        Section section = Section.of(TwoColumnLayout.ID)
                .withComponent(html("fares", TwoColumnLayout.SECOND, 0, "Fare table"));

        assertThat(region(page(section).selectFirst(".layout"), TwoColumnLayout.FIRST).children()).isEmpty();
    }

    @Test
    void aSectionOfALayoutTheSiteDoesNotHaveIsLeftOut() {
        Section missing = Section.of("layout_retired")
                .withComponent(html("lost", OneColumnLayout.CONTENT, 0, "Lost"));
        Section kept = Section.of(OneColumnLayout.ID);

        assertThat(page(missing, kept).select(".layout").eachAttr("data-layout")).containsExactly(OneColumnLayout.ID);
    }

    @Test
    void aBlockInARegionTheLayoutDoesNotHaveIsLeftOut() {
        Section section = Section.of(OneColumnLayout.ID)
                .withComponent(html("stray", TwoColumnLayout.SECOND, 0, "Stray"));

        assertThat(page(section).select(".block")).isEmpty();
    }

    @Test
    void aBlockOfAPluginTheSiteDoesNotHaveIsLeftOut() {
        Section section = Section.of(OneColumnLayout.ID).withComponent(new SectionComponent(
                OneColumnLayout.CONTENT, 0, BlockInstance.of("gone", "retired_block", "Gone")));

        assertThat(page(section).select(".block")).isEmpty();
    }

    @Test
    void aBlockWithNothingToShowIsLeftOut() {
        Section section = Section.of(OneColumnLayout.ID).withComponent(new SectionComponent(
                OneColumnLayout.CONTENT, 0, BlockInstance.of("empty", CustomHtmlBlock.ID, "Empty")));

        assertThat(page(section).select(".block")).isEmpty();
    }

    @Test
    void whatABlockDependsOnBubblesToThePage() {
        Section section = Section.of(OneColumnLayout.ID).withComponent(new SectionComponent(
                OneColumnLayout.CONTENT, 0, BlockInstance.of("title", PageTitleBlock.ID, "Title")));

        assertThat(rendered(List.of(section)).cache().contexts()).contains("url.path");
    }

    @Test
    void sectionsRoundTripThroughTheConfigStore() {
        Section section = Section.of(TwoColumnLayout.ID).withColumnWidths("25-75")
                .withComponent(html("fares", TwoColumnLayout.FIRST, 3, "Fare table"));
        try {
            configStore.save(SAVED_SECTIONS, section);

            assertThat(configStore.read(SAVED_SECTIONS, Section.class, null)).isEqualTo(section);
        } finally {
            configStore.delete(SAVED_SECTIONS);
        }
    }
}
