package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockManager;
import dev.springdrop.kernel.block.BlockPlacementManager;
import dev.springdrop.kernel.block.content.BlockContentBlock;
import dev.springdrop.kernel.block.content.BlockContentEntityType;
import dev.springdrop.kernel.block.content.BlockContentService;
import dev.springdrop.kernel.entity.BundleDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.menu.MenuManager;
import dev.springdrop.kernel.render.CacheMetadata;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.theme.Theme;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class CustomBlockAdminIntegrationTest extends AbstractIntegrationTest {

    private static final String BASIC = "basic";

    private static final String SUMMARY = "summary";

    private static final int SUMMARY_MAX_LENGTH = 40;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BlockContentService library;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private BlockPlacementManager placements;

    @Autowired
    private BlockManager blocks;

    @Autowired
    private MenuManager menus;

    @Autowired
    private MenuLinkContentService storedLinks;

    @BeforeEach
    void aBasicBlockTypeWithASummary() {
        menus.install();
        storedLinks.install();
        library.install();
        clearLibrary();
        library.saveType(new BundleDefinition(BASIC, "Basic block"));
        fields.createStorage(new FieldStorageConfig(SUMMARY, BlockContentEntityType.ID, StringFieldType.ID, 1,
                Map.of("max_length", SUMMARY_MAX_LENGTH)));
        fields.createInstance(FieldInstanceConfig.of(SUMMARY, BlockContentEntityType.ID, BASIC, "Summary"));
    }

    @AfterEach
    void theLibraryAsItWas() {
        clearLibrary();
    }

    private void clearLibrary() {
        placements.all().forEach(placement -> placements.delete(placement.id()));
        library.all().forEach(block -> library.delete(((Number) block.id()).longValue()));
        fields.findStorage(BlockContentEntityType.ID, SUMMARY)
                .ifPresent(storage -> fields.deleteStorage(BlockContentEntityType.ID, SUMMARY));
        library.types().forEach(type -> library.deleteType(type.id()));
    }

    private static RequestPostProcessor blockAdministrator() {
        return user("admin").authorities(new SimpleGrantedAuthority(Permissions.ADMINISTER_BLOCKS));
    }

    private Document page(String path) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(blockAdministrator()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private Document submit(MockHttpServletRequestBuilder request) throws Exception {
        return Jsoup.parse(mockMvc.perform(request.with(blockAdministrator()).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private EntityData written(String info, String summary) {
        return library.save(EntityData.of(BlockContentEntityType.ID, null, BASIC, info, Map.of(SUMMARY, summary)));
    }

    private static long idOf(EntityData block) {
        return ((Number) block.id()).longValue();
    }

    @Test
    void theBlockTypesAreClosedToSomeoneWithoutThePermission() throws Exception {
        mockMvc.perform(get(BlockContentTypeController.PATH).with(user("visitor")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(CustomBlockController.PATH).with(user("visitor")))
                .andExpect(status().isForbidden());
    }

    @Test
    void eachBlockTypeLinksToTheFieldsItCarries() throws Exception {
        Document document = page(BlockContentTypeController.PATH);

        assertThat(document.select("tbody td:first-child").text()).isEqualTo("Basic block");
        assertThat(document.selectFirst("a:contains(Manage fields)").attr("href"))
                .isEqualTo(FieldUiController.fieldsPath(BlockContentEntityType.ID, BASIC));
        BootstrapAssertions.assertNoOutlineButtons(document);
    }

    @Test
    void addingABlockTypeNamesItAfterItsLabel() throws Exception {
        mockMvc.perform(post(BlockContentTypeController.PATH + "/add")
                        .param(BlockContentTypeController.LABEL, "Event notice")
                        .with(blockAdministrator())
                        .with(csrf()))
                .andExpect(redirectedUrl(BlockContentTypeController.PATH));

        assertThat(library.findType("event_notice"))
                .hasValueSatisfying(type -> assertThat(type.label()).isEqualTo("Event notice"));
    }

    @Test
    void aBlockTypeWithNoNameIsRefused() throws Exception {
        Document document = submit(post(BlockContentTypeController.PATH + "/add")
                .param(BlockContentTypeController.LABEL, ""));

        assertThat(document.selectFirst("input[name=label]").hasClass("is-invalid")).isTrue();
        assertThat(library.types()).extracting(BundleDefinition::id).containsExactly(BASIC);
    }

    @Test
    void anUnusedBlockTypeIsDeletedOnceConfirmed() throws Exception {
        Document confirm = page(BlockContentTypeController.PATH + "/manage/" + BASIC + "/delete");
        assertThat(confirm.selectFirst("button[type=submit]").text()).isEqualTo("Delete block type");

        mockMvc.perform(post(BlockContentTypeController.PATH + "/manage/" + BASIC + "/delete")
                        .with(blockAdministrator())
                        .with(csrf()))
                .andExpect(redirectedUrl(BlockContentTypeController.PATH));
        assertThat(library.findType(BASIC)).isEmpty();
    }

    @Test
    void aBlockTypeTheLibraryStillUsesCannotBeDeleted() throws Exception {
        written("Opening hours", "Nine to five.");

        Document confirm = page(BlockContentTypeController.PATH + "/manage/" + BASIC + "/delete");
        assertThat(confirm.select("button[type=submit]")).isEmpty();

        mockMvc.perform(post(BlockContentTypeController.PATH + "/manage/" + BASIC + "/delete")
                        .with(blockAdministrator())
                        .with(csrf()))
                .andExpect(redirectedUrl(BlockContentTypeController.PATH + "/manage/" + BASIC + "/delete"));
        assertThat(library.findType(BASIC)).isPresent();
    }

    @Test
    void aBlockTypeTheSiteDoesNotHaveCannotBeDeletedOrWrittenIn() throws Exception {
        mockMvc.perform(get(BlockContentTypeController.PATH + "/manage/nonesuch/delete").with(blockAdministrator()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(CustomBlockController.PATH + "/add/nonesuch").with(blockAdministrator()))
                .andExpect(status().isNotFound());
    }

    @Test
    void theLibraryListsItsBlocksWithAnAddButtonPerType() throws Exception {
        written("Opening hours", "Nine to five.");

        Document document = page(CustomBlockController.PATH);

        assertThat(document.selectFirst("a[href=" + CustomBlockController.PATH + "/add/basic]").text())
                .isEqualTo("Add Basic block block");
        assertThat(document.select("tbody td:first-child").text()).isEqualTo("Opening hours");
        assertThat(document.select("tbody td:nth-child(2)").text()).isEqualTo("Basic block");
        BootstrapAssertions.assertEditControlsAreButtons(document);
    }

    @Test
    void writingABlockAsksForItsDescriptionAndTheFieldsItsTypeCarries() throws Exception {
        Document document = page(CustomBlockController.PATH + "/add/" + BASIC);

        assertThat(document.selectFirst("input[name=" + CustomBlockController.INFO + "]").hasAttr("required"))
                .isTrue();
        assertThat(document.selectFirst("input[name=" + SUMMARY + "]")).isNotNull();
    }

    @Test
    void aBlockWrittenInTheLibraryIsStoredWithItsFields() throws Exception {
        mockMvc.perform(post(CustomBlockController.PATH + "/add/" + BASIC)
                        .param(CustomBlockController.INFO, "Opening hours")
                        .param(SUMMARY, "Nine to five.")
                        .with(blockAdministrator())
                        .with(csrf()))
                .andExpect(redirectedUrl(CustomBlockController.PATH));

        assertThat(library.all()).singleElement().satisfies(block -> {
            assertThat(block.label()).isEqualTo("Opening hours");
            assertThat(block.bundle()).isEqualTo(BASIC);
            assertThat(block.fields()).containsEntry(SUMMARY, "Nine to five.");
        });
    }

    @Test
    void aBlockWithNoDescriptionComesBackWithTheError() throws Exception {
        Document document = submit(post(CustomBlockController.PATH + "/add/" + BASIC)
                .param(CustomBlockController.INFO, "")
                .param(SUMMARY, "Nine to five."));

        assertThat(document.selectFirst("input[name=info]").hasClass("is-invalid")).isTrue();
        assertThat(document.selectFirst("input[name=" + SUMMARY + "]").val()).isEqualTo("Nine to five.");
        assertThat(library.all()).isEmpty();
    }

    @Test
    void aFieldValueTheEntityRefusesComesBackWithTheErrorOnThatField() throws Exception {
        Document document = submit(post(CustomBlockController.PATH + "/add/" + BASIC)
                .param(CustomBlockController.INFO, "Opening hours")
                .param(SUMMARY, "x".repeat(SUMMARY_MAX_LENGTH + 1)));

        assertThat(document.selectFirst("input[name=" + SUMMARY + "]").hasClass("is-invalid")).isTrue();
        assertThat(library.all()).isEmpty();
    }

    @Test
    void editingABlockShowsWhatItHoldsAndSavesANewRevision() throws Exception {
        EntityData block = written("Opening hours", "Nine to five.");
        String path = CustomBlockController.PATH + "/" + idOf(block) + "/edit";

        assertThat(page(path).selectFirst("input[name=" + SUMMARY + "]").val()).isEqualTo("Nine to five.");

        mockMvc.perform(post(path)
                        .param(CustomBlockController.INFO, "Opening hours")
                        .param(SUMMARY, "Ten to four.")
                        .with(blockAdministrator())
                        .with(csrf()))
                .andExpect(redirectedUrl(CustomBlockController.PATH));

        assertThat(library.find(idOf(block))).hasValueSatisfying(saved -> {
            assertThat(saved.fields()).containsEntry(SUMMARY, "Ten to four.");
            assertThat(saved.uuid()).isEqualTo(block.uuid());
            assertThat(saved.revisionId()).isGreaterThan(block.revisionId());
        });
    }

    @Test
    void aBlockIsDeletedOnceConfirmed() throws Exception {
        EntityData block = written("Opening hours", "Nine to five.");
        String path = CustomBlockController.PATH + "/" + idOf(block) + "/delete";

        assertThat(page(path).selectFirst("h1").text()).isEqualTo("Delete the custom block Opening hours?");

        mockMvc.perform(post(path).with(blockAdministrator()).with(csrf()))
                .andExpect(redirectedUrl(CustomBlockController.PATH));
        assertThat(library.find(idOf(block))).isEmpty();
    }

    @Test
    void aBlockTheLibraryDoesNotHoldCannotBeEdited() throws Exception {
        mockMvc.perform(get(CustomBlockController.PATH + "/999999/edit").with(blockAdministrator()))
                .andExpect(status().isNotFound());
    }

    @Test
    void aCustomBlockCreatedInTheLibraryIsPlacedInARegionAndRendersItsFields() throws Exception {
        mockMvc.perform(post(CustomBlockController.PATH + "/add/" + BASIC)
                        .param(CustomBlockController.INFO, "Opening hours")
                        .param(SUMMARY, "Nine to five, Monday to Friday.")
                        .with(blockAdministrator())
                        .with(csrf()))
                .andExpect(redirectedUrl(CustomBlockController.PATH));
        String blockId = String.valueOf(library.all().getFirst().id());

        mockMvc.perform(post(BlockLayoutController.PATH + "/add/" + BlockContentBlock.ID + "/" + Theme.FRONT_END)
                        .param(BlockLayoutController.LABEL, "Opening hours")
                        .param(BlockLayoutController.LABEL_DISPLAY, "on")
                        .param(BlockLayoutController.REGION, "sidebar")
                        .param(BlockLayoutController.WEIGHT, "0")
                        .param(BlockContentBlock.BLOCK_ELEMENT, blockId)
                        .with(blockAdministrator())
                        .with(csrf()))
                .andExpect(redirectedUrl(BlockLayoutController.layoutPath(Theme.FRONT_END)));

        Document frontPage = Jsoup.parse(mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(frontPage.selectFirst(".region-sidebar h2").text()).isEqualTo("Opening hours");
        assertThat(frontPage.selectFirst(".region-sidebar .field-" + SUMMARY + " .field-label").text())
                .isEqualTo("Summary");
        assertThat(frontPage.selectFirst(".region-sidebar .field-" + SUMMARY + " .field-item").text())
                .isEqualTo("Nine to five, Monday to Friday.");
    }

    @Test
    void theCustomBlockSettingOffersEveryBlockInTheLibrary() throws Exception {
        EntityData block = written("Opening hours", "Nine to five.");

        Document document = page(BlockLayoutController.PATH + "/add/" + BlockContentBlock.ID + "/" + Theme.FRONT_END);

        assertThat(document.select("select[name=" + BlockContentBlock.BLOCK_ELEMENT + "] option"))
                .extracting(option -> option.val() + " " + option.text())
                .contains(block.id() + " Opening hours");
    }

    @Test
    void aPlacedCustomBlockIsInvalidatedWithTheBlockItShows() {
        EntityData block = written("Opening hours", "Nine to five.");

        assertThat(blocks.plugin(BlockContentBlock.ID)
                .cacheability(Map.of(BlockContentBlock.BLOCK, String.valueOf(block.id()))).tags())
                .containsExactly(BlockContentService.cacheTag(idOf(block)));
    }

    @Test
    void aPlacementNamingNoBlockShowsNothingAndDependsOnNothing() {
        BlockContext context = BlockContext.of("/", "Home");

        assertThat(blocks.plugin(BlockContentBlock.ID).build(context, Map.of())).isEmpty();
        assertThat(blocks.plugin(BlockContentBlock.ID).build(context, Map.of(BlockContentBlock.BLOCK, "999999")))
                .isEmpty();
        assertThat(blocks.plugin(BlockContentBlock.ID).cacheability(Map.of())).isEqualTo(CacheMetadata.EMPTY);
    }
}
