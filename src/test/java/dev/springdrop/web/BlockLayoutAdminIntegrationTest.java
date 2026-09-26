package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.block.BlockPlacement;
import dev.springdrop.kernel.block.BlockPlacementManager;
import dev.springdrop.kernel.block.ConditionConfig;
import dev.springdrop.kernel.block.conditions.RequestPathCondition;
import dev.springdrop.kernel.block.conditions.UserRoleCondition;
import dev.springdrop.kernel.block.content.BlockContentBlock;
import dev.springdrop.kernel.block.plugins.CustomHtmlBlock;
import dev.springdrop.kernel.block.plugins.PoweredByBlock;
import dev.springdrop.kernel.block.plugins.SiteBrandingBlock;
import dev.springdrop.kernel.block.content.BlockContentService;
import dev.springdrop.kernel.role.RoleConfig;
import dev.springdrop.kernel.role.RoleManager;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.theme.Theme;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
class BlockLayoutAdminIntegrationTest extends AbstractIntegrationTest {

    private static final String LAYOUT = BlockLayoutController.layoutPath(Theme.FRONT_END);

    private static final String ADD_NOTICE =
            BlockLayoutController.PATH + "/add/" + CustomHtmlBlock.ID + "/" + Theme.FRONT_END;

    private static final String EDITOR = "editor";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BlockPlacementManager placements;

    @Autowired
    private RoleManager roles;

    @Autowired
    private BlockContentService library;

    @BeforeEach
    void noBlocksPlaced() {
        library.install();
        roles.install();
        roles.save(RoleConfig.of(EDITOR, "Editor", 5));
        placements.all().forEach(placement -> placements.delete(placement.id()));
    }

    @AfterEach
    void noBlocksLeftBehind() {
        placements.all().forEach(placement -> placements.delete(placement.id()));
        roles.delete(EDITOR);
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

    private static BlockPlacement notice(String id, String region, int weight) {
        return BlockPlacement.of(id, Theme.FRONT_END, region, CustomHtmlBlock.ID, "Notice " + id)
                .withSettings(Map.of(CustomHtmlBlock.BODY, "<p>Closed Monday.</p>"))
                .withWeight(weight);
    }

    private static MockHttpServletRequestBuilder validNotice(String path) {
        return notice(path, Map.of());
    }

    /** A notice submission with some of its values replaced. */
    private static MockHttpServletRequestBuilder notice(String path, Map<String, String> replaced) {
        Map<String, String> values = new LinkedHashMap<>(Map.of(
                BlockLayoutController.LABEL, "Office hours",
                BlockLayoutController.LABEL_DISPLAY, "on",
                BlockLayoutController.REGION, "sidebar",
                BlockLayoutController.WEIGHT, "4",
                CustomHtmlBlock.BODY_ELEMENT, "<p>Open nine to five.</p>"));
        values.putAll(replaced);
        MockHttpServletRequestBuilder request = post(path);
        values.forEach(request::param);
        return request;
    }

    @Test
    void theBlockLayoutIsClosedToSomeoneWithoutThePermission() throws Exception {
        mockMvc.perform(get(BlockLayoutController.PATH).with(user("visitor")))
                .andExpect(status().isForbidden());
    }

    @Test
    void theBlockLayoutOpensOnTheFrontEndThemeWithATabPerTheme() throws Exception {
        Document document = page(BlockLayoutController.PATH);

        assertThat(document.select(".nav-tabs .nav-link")).extracting(link -> link.text())
                .contains(Theme.ADMIN, Theme.FRONT_END);
        assertThat(document.selectFirst(".nav-tabs .nav-link.active").text()).isEqualTo(Theme.FRONT_END);
    }

    @Test
    void theLayoutListsEveryRegionOfTheThemeWithTheBlocksPlacedInIt() throws Exception {
        placements.save(notice("hours", "sidebar", 0));

        Document document = page(LAYOUT);

        assertThat(document.select("section[data-region]")).extracting(section -> section.attr("data-region"))
                .containsExactly("header", "primary_menu", "breadcrumb", "highlighted", "help", "content",
                        "sidebar", "footer");
        assertThat(document.select("section[data-region=sidebar] tbody td:first-child").text())
                .isEqualTo("Notice hours");
        assertThat(document.select("section[data-region=sidebar] tbody td:nth-child(2)").text())
                .isEqualTo("Custom HTML");
    }

    @Test
    void theLayoutControlsAreSolidButtonsWithEditAsAButton() throws Exception {
        placements.save(notice("hours", "sidebar", 0));

        Document document = page(LAYOUT);

        BootstrapAssertions.assertEditControlsAreButtons(document);
        BootstrapAssertions.assertNoOutlineButtons(document);
    }

    @Test
    void aPlacementOfABlockTheSiteNoLongerHasIsMarkedMissing() throws Exception {
        placements.save(BlockPlacement.of("gone", Theme.FRONT_END, "sidebar", "vanished_block", "Gone"));

        assertThat(page(LAYOUT).select("section[data-region=sidebar] tbody td:nth-child(2)").text())
                .isEqualTo("vanished_block (missing)");
    }

    @Test
    void theAdminThemeHasALayoutOfItsOwn() throws Exception {
        Document document = page(BlockLayoutController.layoutPath(Theme.ADMIN));

        assertThat(document.selectFirst(".nav-tabs .nav-link.active").text()).isEqualTo(Theme.ADMIN);
        assertThat(document.select("section[data-region=sidebar]")).isEmpty();
    }

    @Test
    void aThemeTheSiteDoesNotHaveHasNoLayout() throws Exception {
        mockMvc.perform(get(BlockLayoutController.layoutPath("nonesuch")).with(blockAdministrator()))
                .andExpect(status().isNotFound());
    }

    @Test
    void savingTheLayoutMovesAndReordersTheBlocks() throws Exception {
        placements.save(notice("hours", "sidebar", 0));
        placements.save(notice("closure", "sidebar", 5));

        mockMvc.perform(post(LAYOUT)
                        .param(BlockLayoutController.REGION_PREFIX + "hours", "footer")
                        .param(BlockLayoutController.WEIGHT_PREFIX + "hours", "3")
                        .param(BlockLayoutController.REGION_PREFIX + "closure", "sidebar")
                        .param(BlockLayoutController.WEIGHT_PREFIX + "closure", "5")
                        .with(blockAdministrator())
                        .with(csrf()))
                .andExpect(redirectedUrl(LAYOUT));

        assertThat(placements.find("hours")).hasValueSatisfying(placement -> {
            assertThat(placement.region()).isEqualTo("footer");
            assertThat(placement.weight()).isEqualTo(3);
        });
        assertThat(placements.find("closure")).contains(notice("closure", "sidebar", 5));
    }

    @Test
    void savingTheLayoutLeavesABlockWhereItWasWhenWhatWasSentWillNotDo() throws Exception {
        placements.save(notice("hours", "sidebar", 2));
        placements.save(notice("closure", "footer", 7));

        mockMvc.perform(post(LAYOUT)
                        .param(BlockLayoutController.REGION_PREFIX + "hours", "attic")
                        .param(BlockLayoutController.WEIGHT_PREFIX + "hours", "heavy")
                        .with(blockAdministrator())
                        .with(csrf()))
                .andExpect(redirectedUrl(LAYOUT));

        assertThat(placements.find("hours")).contains(notice("hours", "sidebar", 2));
        assertThat(placements.find("closure")).contains(notice("closure", "footer", 7));
    }

    @Test
    void theBlockLibraryOffersEveryBlockForTheRegionChosen() throws Exception {
        Document document = page(BlockLayoutController.PATH + "/library/" + Theme.FRONT_END + "?region=footer");

        assertThat(document.select("tbody td:first-child")).extracting(cell -> cell.text())
                .contains("Custom HTML", "Site branding", "Powered by SpringDrop");
        assertThat(document.select("a[href$=?region=footer]")).isNotEmpty();
        assertThat(document.selectFirst("a[href=" + ADD_NOTICE + "?region=footer]")).isNotNull();
    }

    @Test
    void placingABlockAsksForItsTitleRegionWeightSettingsAndVisibility() throws Exception {
        Document document = page(ADD_NOTICE + "?region=footer");

        assertThat(document.selectFirst("input[name=label]").val()).isEqualTo("Custom HTML");
        assertThat(document.selectFirst("select[name=region] option[selected]").val()).isEqualTo("footer");
        assertThat(document.selectFirst("fieldset legend").text()).isEqualTo("Block settings");
        assertThat(document.selectFirst("textarea[name=" + CustomHtmlBlock.BODY_ELEMENT + "]")).isNotNull();
        assertThat(document.select("details summary")).extracting(summary -> summary.text())
                .contains("Pages", "Roles", "Language");
        assertThat(document.selectFirst("input[name=visibility_user_role_editor]")).isNotNull();
    }

    @Test
    void aBlockWithNoSettingsIsPlacedWithoutASettingsFieldset() throws Exception {
        Document document = page(BlockLayoutController.PATH + "/add/" + SiteBrandingBlock.ID + "/" + Theme.FRONT_END);

        assertThat(document.select("fieldset legend")).extracting(legend -> legend.text())
                .containsExactly("Visibility");
    }

    @Test
    void placingABlockSavesItWithItsSettingsAndConditionsAndReturnsToTheLayout() throws Exception {
        mockMvc.perform(validNotice(ADD_NOTICE)
                        .param("visibility_user_role_editor", "on")
                        .param("visibility_request_path_pages", "/admin/*")
                        .param("visibility_request_path_negate", "on")
                        .with(blockAdministrator())
                        .with(csrf()))
                .andExpect(redirectedUrl(LAYOUT));

        assertThat(placements.find("front_end_office_hours")).hasValueSatisfying(placement -> {
            assertThat(placement.region()).isEqualTo("sidebar");
            assertThat(placement.weight()).isEqualTo(4);
            assertThat(placement.labelDisplay()).isTrue();
            assertThat(placement.settings()).containsEntry(CustomHtmlBlock.BODY, "<p>Open nine to five.</p>");
            assertThat(placement.visibility()).containsExactlyInAnyOrder(
                    ConditionConfig.of(UserRoleCondition.ID, Map.of(UserRoleCondition.ROLES, List.of(EDITOR))),
                    ConditionConfig.of(RequestPathCondition.ID, Map.of(RequestPathCondition.PAGES, "/admin/*"))
                            .negated());
        });
    }

    @Test
    void placingTheSameBlockTwiceGivesTheSecondPlacementAnIdOfItsOwn() throws Exception {
        placements.save(notice("front_end_office_hours", "sidebar", 0));

        mockMvc.perform(validNotice(ADD_NOTICE).with(blockAdministrator()).with(csrf()))
                .andExpect(redirectedUrl(LAYOUT));

        assertThat(placements.find("front_end_office_hours_2")).isPresent();
    }

    @Test
    void aBlockPlacedWithoutATitleComesBackWithTheErrorAndWhatWasTyped() throws Exception {
        Document document = submit(notice(ADD_NOTICE, Map.of(
                BlockLayoutController.LABEL, "",
                BlockLayoutController.WEIGHT, "heavy")));

        assertThat(document.selectFirst("input[name=label]").hasClass("is-invalid")).isTrue();
        assertThat(document.selectFirst("input[name=weight]").hasClass("is-invalid")).isTrue();
        assertThat(document.selectFirst("textarea[name=" + CustomHtmlBlock.BODY_ELEMENT + "]").text())
                .isEqualTo("<p>Open nine to five.</p>");
        assertThat(placements.all()).isEmpty();
    }

    @Test
    void aRegionTheThemeDoesNotHaveIsRefused() throws Exception {
        Document document = submit(notice(ADD_NOTICE, Map.of(BlockLayoutController.REGION, "attic")));

        assertThat(document.selectFirst("select[name=region] + .invalid-feedback").text())
                .isEqualTo(BlockLayoutController.UNKNOWN_REGION_MESSAGE);
        assertThat(placements.all()).isEmpty();
    }

    @Test
    void aPluginsOwnChecksRefuseWhatTheElementRulesLetThrough() throws Exception {
        Document document = submit(post(BlockLayoutController.PATH + "/add/" + BlockContentBlock.ID + "/"
                        + Theme.FRONT_END)
                .param(BlockLayoutController.LABEL, "Promotion")
                .param(BlockLayoutController.REGION, "sidebar")
                .param(BlockLayoutController.WEIGHT, "0")
                .param(BlockContentBlock.BLOCK_ELEMENT, "999999"));

        assertThat(document.text()).contains(BlockContentBlock.UNKNOWN_BLOCK_MESSAGE);
        assertThat(placements.all()).isEmpty();
    }

    @Test
    void aBlockTheSiteDoesNotHaveCannotBePlaced() throws Exception {
        mockMvc.perform(get(BlockLayoutController.PATH + "/add/vanished/" + Theme.FRONT_END)
                        .with(blockAdministrator()))
                .andExpect(status().isNotFound());
    }

    @Test
    void aBlockCannotBePlacedInAThemeTheSiteDoesNotHave() throws Exception {
        mockMvc.perform(validNotice(BlockLayoutController.PATH + "/add/" + CustomHtmlBlock.ID + "/nonesuch")
                        .with(blockAdministrator())
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void configuringABlockShowsWhatItWasPlacedWith() throws Exception {
        placements.save(notice("hours", "footer", 6).withoutLabel().withVisibility(List.of(
                ConditionConfig.of(UserRoleCondition.ID, Map.of(UserRoleCondition.ROLES, List.of(EDITOR)))
                        .negated())));

        Document document = page(BlockLayoutController.PATH + "/manage/hours");

        assertThat(document.selectFirst("input[name=label]").val()).isEqualTo("Notice hours");
        assertThat(document.selectFirst("input[name=label_display]").hasAttr("checked")).isFalse();
        assertThat(document.selectFirst("input[name=weight]").val()).isEqualTo("6");
        assertThat(document.selectFirst("input[name=visibility_user_role_editor]").hasAttr("checked")).isTrue();
        assertThat(document.selectFirst("input[name=visibility_user_role_negate]").hasAttr("checked")).isTrue();
        assertThat(document.selectFirst("input[name=visibility_language_negate]").hasAttr("checked")).isFalse();
    }

    @Test
    void savingAConfiguredBlockKeepsItsIdAndReplacesItsSettings() throws Exception {
        placements.save(notice("hours", "footer", 6));

        mockMvc.perform(validNotice(BlockLayoutController.PATH + "/manage/hours")
                        .with(blockAdministrator())
                        .with(csrf()))
                .andExpect(redirectedUrl(LAYOUT));

        assertThat(placements.find("hours")).hasValueSatisfying(placement -> {
            assertThat(placement.label()).isEqualTo("Office hours");
            assertThat(placement.region()).isEqualTo("sidebar");
            assertThat(placement.visibility()).isEmpty();
        });
        assertThat(placements.all()).hasSize(1);
    }

    @Test
    void aConfiguredBlockWithAnErrorKeepsItsWeightWhenTheNewOneWillNotDo() throws Exception {
        placements.save(notice("hours", "footer", 6));

        Document document = submit(notice(BlockLayoutController.PATH + "/manage/hours",
                Map.of(BlockLayoutController.WEIGHT, "heavy")));

        assertThat(document.selectFirst("input[name=weight]").val()).isEqualTo("6");
        assertThat(placements.find("hours")).contains(notice("hours", "footer", 6));
    }

    @Test
    void aPlacementTheSiteDoesNotHaveCannotBeConfigured() throws Exception {
        mockMvc.perform(get(BlockLayoutController.PATH + "/manage/nonesuch").with(blockAdministrator()))
                .andExpect(status().isNotFound());
    }

    @Test
    void removingABlockAsksFirstAndThenTakesItOutOfTheLayout() throws Exception {
        placements.save(BlockPlacement.of("credit", Theme.FRONT_END, "footer", PoweredByBlock.ID, "Credit"));

        Document confirm = page(BlockLayoutController.PATH + "/manage/credit/delete");
        assertThat(confirm.selectFirst("h1").text()).isEqualTo("Remove the block Credit?");
        assertThat(placements.find("credit")).isPresent();

        mockMvc.perform(post(BlockLayoutController.PATH + "/manage/credit/delete")
                        .with(blockAdministrator())
                        .with(csrf()))
                .andExpect(redirectedUrl(LAYOUT));
        assertThat(placements.find("credit")).isEmpty();
    }
}
