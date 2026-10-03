package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockDefinition;
import dev.springdrop.kernel.block.BlockInstance;
import dev.springdrop.kernel.block.BlockManager;
import dev.springdrop.kernel.block.plugins.CustomHtmlBlock;
import dev.springdrop.kernel.block.plugins.FieldBlock;
import dev.springdrop.kernel.block.plugins.FieldBlockDeriver;
import dev.springdrop.kernel.block.plugins.PoweredByBlock;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.display.FieldDisplaySlot;
import dev.springdrop.kernel.field.display.ViewDisplayConfig;
import dev.springdrop.kernel.field.display.ViewDisplayManager;
import dev.springdrop.kernel.field.formatter.types.BasicStringFormatter;
import dev.springdrop.kernel.field.formatter.types.StringFormatter;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.layout.LayoutBuilderDisplay;
import dev.springdrop.kernel.layout.LayoutDisplayManager;
import dev.springdrop.kernel.layout.Section;
import dev.springdrop.kernel.layout.SectionComponent;
import dev.springdrop.kernel.layout.layouts.OneColumnLayout;
import dev.springdrop.kernel.layout.layouts.TwoColumnLayout;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.menu.MenuManager;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodePermissions;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.render.RenderService;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.theme.Theme;
import dev.springdrop.kernel.user.UserEntityType;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class LayoutBuilderIntegrationTest extends AbstractIntegrationTest {

    private static final String ARTICLE = "article";

    private static final String PAGE = "page";

    private static final String SUBTITLE = "subtitle";

    private static final String SUMMARY = "summary";

    private static final String FULL = ViewDisplayConfig.FULL_MODE;

    private static final String LAYOUT = LayoutBuilderController.layoutPath(NodeEntityType.ID, ARTICLE, FULL);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NodeService nodes;

    @Autowired
    private NodeTypeManager types;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private ViewDisplayManager viewDisplays;

    @Autowired
    private LayoutDisplayManager layoutDisplays;

    @Autowired
    private BlockManager blocks;

    @Autowired
    private RenderService renderer;

    @Autowired
    private MenuManager menus;

    @Autowired
    private MenuLinkContentService storedLinks;

    @BeforeEach
    void anArticleWithASubtitleAndASummary() {
        menus.install();
        storedLinks.install();
        nodes.install();
        clearContent();
        types.save(NodeType.of(ARTICLE, "Article"));
        types.save(NodeType.of(PAGE, "Basic page"));
        fields.createStorage(new FieldStorageConfig(SUBTITLE, NodeEntityType.ID, StringFieldType.ID, 1, Map.of()));
        fields.createInstance(FieldInstanceConfig.of(SUBTITLE, NodeEntityType.ID, ARTICLE, "Subtitle"));
        fields.createStorage(new FieldStorageConfig(SUMMARY, NodeEntityType.ID, StringFieldType.ID, 1, Map.of()));
        fields.createInstance(FieldInstanceConfig.of(SUMMARY, NodeEntityType.ID, ARTICLE, "Summary"));
        fields.createInstance(FieldInstanceConfig.of(SUMMARY, NodeEntityType.ID, PAGE, "Page summary"));
    }

    @AfterEach
    void noContentLeft() {
        clearContent();
    }

    private void clearContent() {
        queries.query(NodeEntityType.ID).ids().forEach(id -> nodes.delete(((Number) id).longValue()));
        List.of(SUBTITLE, SUMMARY).forEach(field -> fields.findStorage(NodeEntityType.ID, field)
                .ifPresent(storage -> fields.deleteStorage(NodeEntityType.ID, field)));
        for (String bundle : List.of(ARTICLE, PAGE)) {
            for (String mode : List.of(FULL, ViewDisplayConfig.TEASER_MODE)) {
                viewDisplays.delete(NodeEntityType.ID, bundle, mode);
                layoutDisplays.delete(NodeEntityType.ID, bundle, mode);
            }
        }
        types.all().forEach(type -> types.delete(type.id()));
    }

    private static RequestPostProcessor fieldAdministrator() {
        return user("admin").authorities(new SimpleGrantedAuthority(Permissions.ADMINISTER_FIELDS));
    }

    private static RequestPostProcessor reader() {
        return user("reader").authorities(new SimpleGrantedAuthority(NodePermissions.ACCESS_CONTENT));
    }

    private Document page(String path) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(fieldAdministrator()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private Document submit(MockHttpServletRequestBuilder request) throws Exception {
        return Jsoup.parse(mockMvc.perform(request.with(fieldAdministrator()).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private void redirectsToTheLayout(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.with(fieldAdministrator()).with(csrf())).andExpect(redirectedUrl(LAYOUT));
    }

    private void notFound(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.with(fieldAdministrator()).with(csrf())).andExpect(status().isNotFound());
    }

    private LayoutBuilderDisplay layout() {
        return layoutDisplays.find(NodeEntityType.ID, ARTICLE, FULL).orElseThrow();
    }

    private void saveLayout(Section... sections) {
        layoutDisplays.save(LayoutBuilderDisplay.of(NodeEntityType.ID, ARTICLE, FULL, List.of(sections)));
    }

    private static SectionComponent fieldBlock(String id, String region, int weight, String field) {
        return new SectionComponent(region, weight, BlockInstance.of(id,
                FieldBlockDeriver.blockId(NodeEntityType.ID, ARTICLE, field), field).withoutLabel());
    }

    private EntityData article() {
        Map<String, Object> values = new LinkedHashMap<>(nodes.create(types.find(ARTICLE).orElseThrow(), 7L).fields());
        values.put(SUBTITLE, "From the front desk");
        values.put(SUMMARY, "Opening hours change in May.");
        return nodes.save(EntityData.of(NodeEntityType.ID, null, ARTICLE, "Spring schedule", values), 7L);
    }

    private Document nodePage(EntityData node) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(NodeEntityType.path(node.id())).with(reader()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static String regionText(Document document, int section, String region) {
        Element drawn = document.select(".node__content .layout").get(section);
        return drawn.selectFirst("[data-region=" + region + "]").text();
    }

    @Test
    void anAdminArrangesFieldBlocksIntoSectionsAndTheNodeRendersThatLayout() throws Exception {
        redirectsToTheLayout(post(LAYOUT + "/enable"));
        redirectsToTheLayout(post(LAYOUT + "/section/0/remove"));
        redirectsToTheLayout(post(LAYOUT + "/section/add").param(LayoutEditor.LAYOUT, TwoColumnLayout.ID));
        redirectsToTheLayout(post(LAYOUT + "/section/0/region/first/add/"
                + FieldBlockDeriver.blockId(NodeEntityType.ID, ARTICLE, SUMMARY))
                .param(LayoutEditor.LABEL, "Summary")
                .param(FieldBlock.FORMATTER_ELEMENT, StringFormatter.ID));
        redirectsToTheLayout(post(LAYOUT + "/section/0/region/second/add/"
                + FieldBlockDeriver.blockId(NodeEntityType.ID, ARTICLE, SUBTITLE))
                .param(LayoutEditor.LABEL, "Subtitle")
                .param(FieldBlock.FORMATTER_ELEMENT, StringFormatter.ID));
        EntityData node = article();

        Document document = nodePage(node);

        assertThat(document.select(".node__content .layout").eachAttr("data-layout"))
                .containsExactly(TwoColumnLayout.ID);
        assertThat(regionText(document, 0, TwoColumnLayout.FIRST)).isEqualTo("Opening hours change in May.");
        assertThat(regionText(document, 0, TwoColumnLayout.SECOND)).isEqualTo("From the front desk");
        Document teaser = Jsoup.parseBodyFragment(
                renderer.render(nodes.build(node, ViewDisplayConfig.TEASER_MODE, false)).html());
        assertThat(teaser.select(".layout")).isEmpty();
    }

    @Nested
    class TurningLayoutBuilderOnAndOff {

        @Test
        void theEditorIsClosedToSomeoneWithoutThePermission() throws Exception {
            mockMvc.perform(get(LAYOUT).with(user("visitor"))).andExpect(status().isForbidden());
        }

        @Test
        void aViewModeLayoutBuilderDoesNotDrawOffersToStart() throws Exception {
            Document document = page(LAYOUT);

            assertThat(document.selectFirst("form[action=" + LAYOUT + "/enable] button").text())
                    .isEqualTo("Use Layout Builder");
        }

        @Test
        void manageDisplayLinksToTheLayout() throws Exception {
            Document document = page(DisplayUiController.viewDisplayPath(NodeEntityType.ID, ARTICLE)
                    + "?" + DisplayUiController.MODE + "=" + FULL);

            assertThat(document.selectFirst("a:contains(Manage layout)").attr("href")).isEqualTo(LAYOUT);
        }

        @Test
        void turningItOnStartsFromTheFieldsAsTheDisplayShowsThem() throws Exception {
            viewDisplays.save(ViewDisplayConfig.of(NodeEntityType.ID, ARTICLE, FULL)
                    .with(FieldDisplaySlot.of(SUMMARY, BasicStringFormatter.ID, 0))
                    .with(FieldDisplaySlot.of(SUBTITLE, StringFormatter.ID, 5))
                    .withoutLabel(SUBTITLE));

            redirectsToTheLayout(post(LAYOUT + "/enable"));

            Section section = layout().sections().getFirst();
            assertThat(section.layout()).isEqualTo(OneColumnLayout.ID);
            assertThat(section.inRegion(OneColumnLayout.CONTENT)).extracting(component -> component.block().plugin())
                    .containsExactly(
                            FieldBlockDeriver.blockId(NodeEntityType.ID, ARTICLE, SUMMARY),
                            FieldBlockDeriver.blockId(NodeEntityType.ID, ARTICLE, SUBTITLE));
            assertThat(section.inRegion(OneColumnLayout.CONTENT)).extracting(component -> component.block().settings())
                    .containsExactly(
                            Map.of(FieldBlock.FORMATTER, BasicStringFormatter.ID),
                            Map.of(FieldBlock.FORMATTER, StringFormatter.ID));
            assertThat(section.inRegion(OneColumnLayout.CONTENT))
                    .extracting(component -> component.block().labelDisplay())
                    .containsExactly(true, false);
        }

        @Test
        void aViewModeWithNoDisplayStartsWithEveryFieldUnderItsDefaultFormatter() throws Exception {
            redirectsToTheLayout(post(LAYOUT + "/enable"));

            assertThat(layout().sections().getFirst().components())
                    .extracting(component -> component.block().settings())
                    .containsOnly(Map.of());
        }

        @Test
        void aNodeReadsTheSameOnceTheLayoutIsFirstTurnedOn() throws Exception {
            EntityData node = article();
            redirectsToTheLayout(post(LAYOUT + "/enable"));

            assertThat(nodePage(node).selectFirst(".node__content [data-region=content]").text())
                    .contains("Subtitle", "From the front desk", "Summary", "Opening hours change in May.");
        }

        @Test
        void turningItOffDrawsTheDisplayAgainAndTurningItBackOnKeepsTheLayout() throws Exception {
            EntityData node = article();
            saveLayout(Section.of(TwoColumnLayout.ID));

            redirectsToTheLayout(post(LAYOUT + "/disable"));
            assertThat(nodePage(node).select(".node__content .layout")).isEmpty();
            assertThat(page(LAYOUT).select("form[action=" + LAYOUT + "/enable]")).isNotEmpty();

            redirectsToTheLayout(post(LAYOUT + "/enable"));
            assertThat(layout().sections()).extracting(Section::layout).containsExactly(TwoColumnLayout.ID);
        }

        @Test
        void turningOffAViewModeThatNeverHadALayoutLeavesNothingBehind() throws Exception {
            redirectsToTheLayout(post(LAYOUT + "/disable"));

            assertThat(layoutDisplays.find(NodeEntityType.ID, ARTICLE, FULL)).isEmpty();
        }

        @Test
        void editingALayoutThatIsNotTurnedOnIsNotFound() throws Exception {
            notFound(post(LAYOUT).param(LayoutEditor.WIDTHS_PREFIX + 0, "33-67"));
        }

        @Test
        void anUnknownEntityTypeBundleOrViewModeIsNotFound() throws Exception {
            notFound(get(LayoutBuilderController.layoutPath("gadget", ARTICLE, FULL)));
            notFound(get(LayoutBuilderController.layoutPath(NodeEntityType.ID, "missing", FULL)));
            notFound(get(LayoutBuilderController.layoutPath(NodeEntityType.ID, ARTICLE, "Full-Mode")));
            notFound(get(LayoutBuilderController.layoutPath(UserEntityType.ID, ARTICLE, FULL)));
        }

        @Test
        void anUnbundledTypesLayoutBelongsToTheTypeItself() throws Exception {
            assertThat(page(LayoutBuilderController.layoutPath(UserEntityType.ID, UserEntityType.ID, FULL))
                    .select("form[action$=/enable]")).isNotEmpty();
        }
    }

    @Nested
    class ArrangingSections {

        @Test
        void theEditorShowsEachSectionWithItsRegionsAndBlocksAsButtons() throws Exception {
            saveLayout(Section.of(TwoColumnLayout.ID).withColumnWidths("33-67")
                    .withComponent(fieldBlock("summary-block", TwoColumnLayout.SECOND, 0, SUMMARY)));

            Document document = page(LAYOUT);

            assertThat(document.selectFirst("[data-section=0] h2").text()).isEqualTo("Section 1: Two column");
            assertThat(document.selectFirst("select[name=" + LayoutEditor.WIDTHS_PREFIX + "0] [selected]")
                    .val()).isEqualTo("33-67");
            assertThat(document.selectFirst("[data-region=second] tr[data-block=summary-block] td").text())
                    .isEqualTo(SUMMARY);
            assertThat(document.selectFirst("[data-region=first]").text()).contains("No blocks in this region.");
            BootstrapAssertions.assertNoOutlineButtons(document);
            BootstrapAssertions.assertEditControlsAreButtons(document);
        }

        @Test
        void aSectionThatHasNotChosenWidthsShowsTheLayoutsFirst() throws Exception {
            saveLayout(Section.of(TwoColumnLayout.ID), Section.of(OneColumnLayout.ID));

            Document document = page(LAYOUT);

            assertThat(document.selectFirst("select[name=" + LayoutEditor.WIDTHS_PREFIX + "0] [selected]")
                    .val()).isEqualTo("50-50");
            assertThat(document.select("select[name=" + LayoutEditor.WIDTHS_PREFIX + "1]")).isEmpty();
        }

        @Test
        void anEmptyLayoutSaysSo() throws Exception {
            saveLayout();

            assertThat(page(LAYOUT).text()).contains("This layout has no sections yet.");
        }

        @Test
        void aSectionOrBlockWhosePluginIsGoneIsMarkedMissing() throws Exception {
            saveLayout(Section.of("layout_retired"), Section.of(OneColumnLayout.ID).withComponent(
                    new SectionComponent(OneColumnLayout.CONTENT, 0, BlockInstance.of("gone", "retired", "Gone"))));

            Document document = page(LAYOUT);

            assertThat(document.selectFirst("[data-section=0] h2").text()).endsWith("layout_retired (missing)");
            assertThat(document.selectFirst("tr[data-block=gone]").text()).contains("retired (missing)");
        }

        @Test
        void savingTheEditorChangesWidthsRegionsAndWeights() throws Exception {
            saveLayout(Section.of(TwoColumnLayout.ID)
                    .withComponent(fieldBlock("summary-block", TwoColumnLayout.FIRST, 0, SUMMARY))
                    .withComponent(fieldBlock("subtitle-block", TwoColumnLayout.FIRST, 1, SUBTITLE)));

            redirectsToTheLayout(post(LAYOUT)
                    .param(LayoutEditor.WIDTHS_PREFIX + 0, "25-75")
                    .param(LayoutEditor.REGION_PREFIX + "summary-block", TwoColumnLayout.SECOND)
                    .param(LayoutEditor.WEIGHT_PREFIX + "subtitle-block", "-3"));

            Section saved = layout().sections().getFirst();
            assertThat(saved.columnWidths()).isEqualTo("25-75");
            assertThat(saved.component("summary-block").orElseThrow().region()).isEqualTo(TwoColumnLayout.SECOND);
            assertThat(saved.component("subtitle-block").orElseThrow().weight()).isEqualTo(-3);
        }

        @Test
        void widthsRegionsAndWeightsTheLayoutCannotTakeAreLeftAsTheyWere() throws Exception {
            Section before = Section.of(TwoColumnLayout.ID).withColumnWidths("33-67")
                    .withComponent(fieldBlock("summary-block", TwoColumnLayout.FIRST, 4, SUMMARY));
            saveLayout(before, Section.of("layout_retired"));

            redirectsToTheLayout(post(LAYOUT)
                    .param(LayoutEditor.WIDTHS_PREFIX + 0, "10-90")
                    .param(LayoutEditor.WIDTHS_PREFIX + 1, "50-50")
                    .param(LayoutEditor.REGION_PREFIX + "summary-block", "third")
                    .param(LayoutEditor.WEIGHT_PREFIX + "summary-block", "heavy"));

            assertThat(layout().sections().getFirst()).isEqualTo(before);
        }

        @Test
        void aSectionIsAddedAfterTheOthers() throws Exception {
            saveLayout(Section.of(OneColumnLayout.ID));

            redirectsToTheLayout(post(LAYOUT + "/section/add")
                    .param(LayoutEditor.LAYOUT, TwoColumnLayout.ID));

            assertThat(layout().sections()).extracting(Section::layout)
                    .containsExactly(OneColumnLayout.ID, TwoColumnLayout.ID);
        }

        @Test
        void aSectionOfALayoutTheSiteDoesNotHaveIsNotAdded() throws Exception {
            saveLayout();

            notFound(post(LAYOUT + "/section/add").param(LayoutEditor.LAYOUT, "layout_retired"));
            assertThat(layout().sections()).isEmpty();
        }

        @Test
        void aSectionIsRemovedWithItsBlocks() throws Exception {
            saveLayout(Section.of(OneColumnLayout.ID), Section.of(TwoColumnLayout.ID)
                    .withComponent(fieldBlock("summary-block", TwoColumnLayout.FIRST, 0, SUMMARY)));

            redirectsToTheLayout(post(LAYOUT + "/section/1/remove"));

            assertThat(layout().sections()).extracting(Section::layout).containsExactly(OneColumnLayout.ID);
        }

        @Test
        void aSectionThatIsNotThereIsNotFound() throws Exception {
            saveLayout(Section.of(OneColumnLayout.ID));

            notFound(post(LAYOUT + "/section/1/remove"));
            notFound(post(LAYOUT + "/section/-1/remove"));
        }
    }

    @Nested
    class PlacingBlocks {

        @BeforeEach
        void aTwoColumnSection() {
            saveLayout(Section.of(TwoColumnLayout.ID));
        }

        @Test
        void theLibraryOffersTheThemesBlocksAndThisBundlesFields() throws Exception {
            Document document = page(LAYOUT + "/section/0/region/first/library");

            List<String> offered = document.select("tbody code").eachText();
            assertThat(offered).contains(
                    PoweredByBlock.ID,
                    FieldBlockDeriver.blockId(NodeEntityType.ID, ARTICLE, SUBTITLE),
                    FieldBlockDeriver.blockId(NodeEntityType.ID, ARTICLE, SUMMARY));
            assertThat(offered).doesNotContain(FieldBlockDeriver.blockId(NodeEntityType.ID, PAGE, SUMMARY));
        }

        @Test
        void aRegionTheSectionDoesNotHaveIsNotFound() throws Exception {
            notFound(get(LAYOUT + "/section/0/region/third/library"));
        }

        @Test
        void aFieldBlockStartsWithItsTitleHiddenAndOtherBlocksWithItShown() throws Exception {
            Document field = page(LAYOUT + "/section/0/region/first/add/"
                    + FieldBlockDeriver.blockId(NodeEntityType.ID, ARTICLE, SUMMARY));
            Document other = page(LAYOUT + "/section/0/region/first/add/" + PoweredByBlock.ID);

            assertThat(field.selectFirst("input[name=" + LayoutEditor.LABEL + "]").val())
                    .isEqualTo("Summary");
            assertThat(field.selectFirst("input[name=" + LayoutEditor.LABEL_DISPLAY + "]")
                    .hasAttr("checked")).isFalse();
            assertThat(field.selectFirst("select[name=" + FieldBlock.FORMATTER_ELEMENT + "] [selected]").val())
                    .isEqualTo(StringFormatter.ID);
            assertThat(other.selectFirst("input[name=" + LayoutEditor.LABEL_DISPLAY + "]")
                    .hasAttr("checked")).isTrue();
        }

        @Test
        void aBlockIsAddedAfterTheBlocksAlreadyInItsRegion() throws Exception {
            redirectsToTheLayout(post(LAYOUT + "/section/0/region/first/add/" + CustomHtmlBlock.ID)
                    .param(LayoutEditor.LABEL, "Notice")
                    .param(CustomHtmlBlock.BODY_ELEMENT, "<p>Office closed</p>"));
            redirectsToTheLayout(post(LAYOUT + "/section/0/region/first/add/" + PoweredByBlock.ID)
                    .param(LayoutEditor.LABEL, "Powered by")
                    .param(LayoutEditor.LABEL_DISPLAY, "on"));

            assertThat(layout().sections().getFirst().inRegion(TwoColumnLayout.FIRST))
                    .extracting(component -> component.block().label(), SectionComponent::weight)
                    .containsExactly(
                            tuple("Notice", 0),
                            tuple("Powered by", 1));
        }

        @Test
        void aBlockWithoutATitleIsNotAdded() throws Exception {
            Document document = submit(post(LAYOUT + "/section/0/region/first/add/" + PoweredByBlock.ID)
                    .param(LayoutEditor.LABEL, ""));

            assertThat(document.selectFirst("input[name=" + LayoutEditor.LABEL + "]")
                    .hasClass("is-invalid")).isTrue();
            assertThat(layout().sections().getFirst().components()).isEmpty();
        }

        @Test
        void aFieldBlockNamingAFormatterTheSiteDoesNotHaveIsNotAdded() throws Exception {
            Document document = submit(post(LAYOUT + "/section/0/region/first/add/"
                    + FieldBlockDeriver.blockId(NodeEntityType.ID, ARTICLE, SUMMARY))
                    .param(LayoutEditor.LABEL, "Summary")
                    .param(FieldBlock.FORMATTER_ELEMENT, "fancy"));

            assertThat(document.text()).contains(FieldBlock.UNKNOWN_FORMATTER_MESSAGE);
            assertThat(layout().sections().getFirst().components()).isEmpty();
        }

        @Test
        void anotherBundlesFieldOrAnUnknownBlockCannotBeAdded() throws Exception {
            notFound(get(LAYOUT + "/section/0/region/first/add/"
                    + FieldBlockDeriver.blockId(NodeEntityType.ID, PAGE, SUMMARY)));
            notFound(post(LAYOUT + "/section/0/region/first/add/retired")
                    .param(LayoutEditor.LABEL, "Retired"));
        }
    }

    @Nested
    class ConfiguringBlocks {

        @BeforeEach
        void aSummaryBlock() {
            saveLayout(Section.of(TwoColumnLayout.ID)
                    .withComponent(fieldBlock("summary-block", TwoColumnLayout.FIRST, 0, SUMMARY))
                    .withComponent(new SectionComponent(TwoColumnLayout.SECOND, 0,
                            BlockInstance.of("gone", "retired", "Gone"))));
        }

        @Test
        void theFormShowsWhatTheBlockHolds() throws Exception {
            Document document = page(LAYOUT + "/block/summary-block");

            assertThat(document.selectFirst("input[name=" + LayoutEditor.LABEL + "]").val())
                    .isEqualTo(SUMMARY);
        }

        @Test
        void savingTheFormChangesTheBlockAndKeepsWhereItIs() throws Exception {
            redirectsToTheLayout(post(LAYOUT + "/block/summary-block")
                    .param(LayoutEditor.LABEL, "In brief")
                    .param(LayoutEditor.LABEL_DISPLAY, "on")
                    .param(FieldBlock.FORMATTER_ELEMENT, BasicStringFormatter.ID));

            SectionComponent saved = layout().sections().getFirst().component("summary-block").orElseThrow();
            assertThat(saved.region()).isEqualTo(TwoColumnLayout.FIRST);
            assertThat(saved.block().label()).isEqualTo("In brief");
            assertThat(saved.block().labelDisplay()).isTrue();
            assertThat(saved.block().settings()).containsEntry(FieldBlock.FORMATTER, BasicStringFormatter.ID);
        }

        @Test
        void anInvalidChangeIsNotSaved() throws Exception {
            Document document = submit(post(LAYOUT + "/block/summary-block")
                    .param(LayoutEditor.LABEL, "")
                    .param(FieldBlock.FORMATTER_ELEMENT, StringFormatter.ID));

            assertThat(document.selectFirst("input[name=" + LayoutEditor.LABEL + "]")
                    .hasClass("is-invalid")).isTrue();
            assertThat(layout().sections().getFirst().component("summary-block").orElseThrow().block().label())
                    .isEqualTo(SUMMARY);
        }

        @Test
        void aBlockThatIsNotThereOrWhosePluginIsGoneCannotBeConfigured() throws Exception {
            notFound(get(LAYOUT + "/block/missing"));
            notFound(get(LAYOUT + "/block/gone"));
            notFound(post(LAYOUT + "/block/gone").param(LayoutEditor.LABEL, "Gone"));
        }

        @Test
        void aBlockIsRemovedEvenWhenItsPluginIsGone() throws Exception {
            redirectsToTheLayout(post(LAYOUT + "/block/gone/remove"));
            redirectsToTheLayout(post(LAYOUT + "/block/summary-block/remove"));

            assertThat(layout().sections().getFirst().components()).isEmpty();
        }

        @Test
        void removingABlockThatIsNotThereIsNotFound() throws Exception {
            notFound(post(LAYOUT + "/block/missing/remove"));
        }
    }

    @Nested
    class FieldBlocks {

        private BlockContext about(EntityData entity) {
            return BlockContext.of("/node/1", "Spring schedule").withRouteEntity(entity);
        }

        @Test
        void aFieldBlockShowsNothingOnAPageAboutNoEntity() {
            assertThat(blocks.plugin(FieldBlockDeriver.blockId(NodeEntityType.ID, ARTICLE, SUMMARY))
                    .build(BlockContext.of("/", "Home"), Map.of())).isEmpty();
        }

        @Test
        void aFieldBlockShowsNothingOnAPageAboutAnotherBundleOrType() {
            EntityData page = EntityData.of(NodeEntityType.ID, 1L, PAGE, "About", Map.of(SUMMARY, "Who we are."));
            EntityData account = EntityData.of(UserEntityType.ID, 1L, null, "edith", Map.of(SUMMARY, "Editor."));

            assertThat(blocks.plugin(FieldBlockDeriver.blockId(NodeEntityType.ID, ARTICLE, SUMMARY))
                    .build(about(page), Map.of())).isEmpty();
            assertThat(blocks.plugin(FieldBlockDeriver.blockId(NodeEntityType.ID, ARTICLE, SUMMARY))
                    .build(about(account), Map.of())).isEmpty();
        }

        @Test
        void aFieldBlockShowsNothingWhenTheFieldIsEmpty() {
            EntityData empty = EntityData.of(NodeEntityType.ID, 1L, ARTICLE, "Spring schedule", Map.of());

            assertThat(blocks.plugin(FieldBlockDeriver.blockId(NodeEntityType.ID, ARTICLE, SUMMARY))
                    .build(about(empty), Map.of())).isEmpty();
        }

        @Test
        void anUnbundledTypesLayoutDrawsItsOwnFields() {
            fields.createStorage(FieldStorageConfig.single("nickname", UserEntityType.ID, StringFieldType.ID));
            fields.createInstance(FieldInstanceConfig.of("nickname", UserEntityType.ID, UserEntityType.ID, "Nickname"));
            try {
                EntityData account = EntityData.of(UserEntityType.ID, 1L, null, "edith", Map.of("nickname", "Edie"));

                layoutDisplays.enable(UserEntityType.ID, UserEntityType.ID, FULL);

                Document drawn = Jsoup.parseBodyFragment(renderer.render(
                        layoutDisplays.render(account, FULL, BlockContext.of("/user/1", "edith")).orElseThrow()).html());

                assertThat(drawn.selectFirst("[data-region=content]").text()).contains("Edie");
            } finally {
                fields.deleteStorage(UserEntityType.ID, "nickname");
                layoutDisplays.delete(UserEntityType.ID, UserEntityType.ID, FULL);
            }
        }

        @Test
        void aNewFieldHasABlockAsSoonAsItExists() {
            fields.createInstance(FieldInstanceConfig.of(SUBTITLE, NodeEntityType.ID, PAGE, "Page subtitle"));

            assertThat(blocks.has(FieldBlockDeriver.blockId(NodeEntityType.ID, PAGE, SUBTITLE))).isTrue();
        }

        @Test
        void theThemesBlockLibraryLeavesFieldBlocksOut() throws Exception {
            assertThat(blocks.definitions()).extracting(BlockDefinition::id)
                    .noneMatch(id -> id.startsWith(FieldBlockDeriver.ID + ":"));
            mockMvc.perform(get(BlockLayoutController.PATH + "/add/"
                            + FieldBlockDeriver.blockId(NodeEntityType.ID, ARTICLE, SUMMARY) + "/" + Theme.FRONT_END)
                            .with(user("admin").authorities(new SimpleGrantedAuthority(Permissions.ADMINISTER_BLOCKS))))
                    .andExpect(status().isNotFound());
        }

        @Test
        void whatALayoutDrawsIsTaggedWithTheLayout() {
            saveLayout(Section.of(OneColumnLayout.ID));
            EntityData node = article();

            assertThat(renderer.render(nodes.build(node, FULL, true)).cache().tags())
                    .contains(layout().cacheTag());
        }

        @Test
        void aNodeWhoseFieldIsLeftEmptyLeavesItsBlockOut() throws Exception {
            saveLayout(Section.of(OneColumnLayout.ID)
                    .withComponent(fieldBlock("summary-block", OneColumnLayout.CONTENT, 0, SUMMARY)));
            Map<String, Object> values = new LinkedHashMap<>(
                    nodes.create(types.find(ARTICLE).orElseThrow(), 7L).fields());
            values.put(BaseFieldDefinition.STATUS, true);
            EntityData node = nodes.save(EntityData.of(NodeEntityType.ID, null, ARTICLE, "Empty", values), 7L);

            assertThat(nodePage(node).select(".node__content .block")).isEmpty();
        }
    }
}
