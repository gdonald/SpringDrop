package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.springdrop.kernel.menu.MenuConfig;
import dev.springdrop.kernel.menu.MenuLink;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.menu.MenuManager;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
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
class MenuAdminIntegrationTest extends AbstractIntegrationTest {

    private static final String TRAVEL = "travel";

    private static final String MANAGE = MenuController.PATH + "/manage/" + TRAVEL;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MenuManager menus;

    @Autowired
    private MenuLinkContentService storedLinks;

    @BeforeEach
    void anEmptyTravelMenu() {
        storedLinks.install();
        menus.install();
        menus.save(MenuConfig.of(TRAVEL, "Travel", "Where the trips are written up."));
        clearTravelLinks();
    }

    @AfterEach
    void theSiteAsItWas() {
        clearTravelLinks();
        menus.delete(TRAVEL);
        menus.delete("trips");
        menus.delete("trips_2");
    }

    private void clearTravelLinks() {
        storedLinks.inMenu(TRAVEL).forEach(link ->
                MenuLinkContentService.entityId(link.id()).ifPresent(storedLinks::delete));
    }

    private static RequestPostProcessor menuAdministrator() {
        return user("admin").authorities(new SimpleGrantedAuthority(Permissions.ADMINISTER_MENU));
    }

    private Document page(String path) throws Exception {
        return Jsoup.parse(mockMvc.perform(get(path).with(menuAdministrator()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private MenuLink stored(String title, String url, int weight) {
        return storedLinks.save(new MenuLink(null, TRAVEL, title, "", url, null, weight, false, true, null));
    }

    private static long entityIdOf(MenuLink link) {
        return MenuLinkContentService.entityId(link.id()).orElseThrow();
    }

    @Test
    void theMenuListIsClosedToSomeoneWithoutThePermission() throws Exception {
        mockMvc.perform(get(MenuController.PATH).with(user("visitor")))
                .andExpect(status().isForbidden());
    }

    @Test
    void theMenuListNamesEveryMenuTheSiteHas() throws Exception {
        assertThat(page(MenuController.PATH).select("tbody tr td:first-child"))
                .extracting(cell -> cell.text())
                .contains("Main navigation", "Administration", "Footer", "User account menu", "Travel");
    }

    @Test
    void aMenuTheSiteReliesOnOffersNoDeleteButton() throws Exception {
        Document document = page(MenuController.PATH);

        assertThat(document.select("a.btn-danger")).extracting(link -> link.attr("href"))
                .containsExactly(MANAGE + "/delete");
    }

    @Test
    void addingAMenuOpensItsLinkAdminAtOnce() throws Exception {
        mockMvc.perform(post(MenuController.PATH + "/add")
                        .param(MenuController.LABEL, "Trips")
                        .param(MenuController.DESCRIPTION, "Trips taken.")
                        .with(menuAdministrator())
                        .with(csrf()))
                .andExpect(redirectedUrl(MenuController.PATH + "/manage/trips"));

        assertThat(menus.find("trips"))
                .hasValueSatisfying(menu -> assertThat(menu.label()).isEqualTo("Trips"));
    }

    @Test
    void asecondMenuOfTheSameNameGetsAnIdOfItsOwn() throws Exception {
        menus.save(MenuConfig.of("trips", "Trips", "Trips taken."));

        mockMvc.perform(post(MenuController.PATH + "/add")
                        .param(MenuController.LABEL, "Trips")
                        .with(menuAdministrator())
                        .with(csrf()))
                .andExpect(redirectedUrl(MenuController.PATH + "/manage/trips_2"));
    }

    @Test
    void deletingAMenuAsksFirstAndThenRemovesIt() throws Exception {
        assertThat(page(MANAGE + "/delete").select("button[type=submit]").text())
                .isEqualTo("Delete menu");

        mockMvc.perform(post(MANAGE + "/delete").with(menuAdministrator()).with(csrf()))
                .andExpect(redirectedUrl(MenuController.PATH));

        assertThat(menus.find(TRAVEL)).isEmpty();
    }

    @Test
    void aMenuTheSiteReliesOnIsNotDeletedEvenWhenTheDeleteIsPosted() throws Exception {
        String mainMenu = MenuController.PATH + "/manage/" + MenuConfig.MAIN;

        assertThat(page(mainMenu + "/delete").select("button[type=submit]")).isEmpty();

        mockMvc.perform(post(mainMenu + "/delete").with(menuAdministrator()).with(csrf()))
                .andExpect(redirectedUrl(mainMenu));

        assertThat(menus.find(MenuConfig.MAIN)).isPresent();
    }

    @Test
    void theLinkAdminListsTheLinksAModuleDeclaredWithoutOfferingToEditThem() throws Exception {
        Document document = page(MenuController.PATH + "/manage/" + MenuConfig.MAIN);

        assertThat(document.select("tbody tr td:first-child")).extracting(cell -> cell.text())
                .contains("Home");
        assertThat(document.select(".badge").text()).contains("Provided by a module");
    }

    @Test
    void addingALinkPutsItInTheMenuItWasAddedTo() throws Exception {
        mockMvc.perform(post(MANAGE + "/link/add")
                        .param(MenuController.TITLE, "Iceland")
                        .param(MenuController.URL, "/iceland")
                        .param(MenuController.DESCRIPTION, "The ring road.")
                        .param(MenuController.ENABLED, "on")
                        .with(menuAdministrator())
                        .with(csrf()))
                .andExpect(redirectedUrl(MANAGE));

        assertThat(storedLinks.inMenu(TRAVEL)).singleElement().satisfies(link -> {
            assertThat(link.title()).isEqualTo("Iceland");
            assertThat(link.url()).isEqualTo("/iceland");
            assertThat(link.enabled()).isTrue();
        });
    }

    @Test
    void theAddLinkFormOffersTheOtherLinksToHangTheNewOneUnder() throws Exception {
        stored("Europe", "/europe", 0);

        Document document = page(MANAGE + "/link/add");

        assertThat(document.select("select[name=" + MenuController.PARENT + "] option"))
                .extracting(option -> option.text())
                .containsExactly("Top of the menu", "Europe");
    }

    @Test
    void aLinkCanBeHungUnderAnotherAndComesBackNested() throws Exception {
        MenuLink europe = stored("Europe", "/europe", 0);

        mockMvc.perform(post(MANAGE + "/link/add")
                        .param(MenuController.TITLE, "Norway")
                        .param(MenuController.URL, "/norway")
                        .param(MenuController.PARENT, europe.id())
                        .param(MenuController.WEIGHT, "4")
                        .param(MenuController.EXPANDED, "on")
                        .param(MenuController.ENABLED, "on")
                        .with(menuAdministrator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(storedLinks.inMenu(TRAVEL))
                .filteredOn(link -> link.title().equals("Norway"))
                .singleElement()
                .satisfies(link -> {
                    assertThat(link.parent()).isEqualTo(europe.id());
                    assertThat(link.weight()).isEqualTo(4);
                    assertThat(link.expanded()).isTrue();
                });
    }

    @Test
    void aLinkAddedWithNothingFilledInHangsAtTheTopAndIsOff() throws Exception {
        mockMvc.perform(post(MANAGE + "/link/add").with(menuAdministrator()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(storedLinks.inMenu(TRAVEL)).singleElement().satisfies(link -> {
            assertThat(link.parent()).isNull();
            assertThat(link.weight()).isZero();
            assertThat(link.enabled()).isFalse();
        });
    }

    @Test
    void editingALinkOpensItWithItsCurrentValues() throws Exception {
        MenuLink iceland = stored("Iceland", "/iceland", 0);

        Document document = page(MenuController.PATH + "/link/" + entityIdOf(iceland) + "/edit");

        assertThat(document.selectFirst("input[name=" + MenuController.TITLE + "]").attr("value"))
                .isEqualTo("Iceland");
        assertThat(document.selectFirst("input[name=" + MenuController.URL + "]").attr("value"))
                .isEqualTo("/iceland");
    }

    @Test
    void savingAnEditedLinkKeepsItInItsOwnMenu() throws Exception {
        MenuLink iceland = stored("Iceland", "/iceland", 0);

        mockMvc.perform(post(MenuController.PATH + "/link/" + entityIdOf(iceland) + "/edit")
                        .param(MenuController.TITLE, "Iceland by road")
                        .param(MenuController.URL, "/iceland")
                        .param(MenuController.ENABLED, "on")
                        .with(menuAdministrator())
                        .with(csrf()))
                .andExpect(redirectedUrl(MANAGE));

        assertThat(storedLinks.find(entityIdOf(iceland)))
                .hasValueSatisfying(link -> assertThat(link.title()).isEqualTo("Iceland by road"));
    }

    @Test
    void deletingALinkAsksFirstAndThenRemovesIt() throws Exception {
        MenuLink iceland = stored("Iceland", "/iceland", 0);
        String path = MenuController.PATH + "/link/" + entityIdOf(iceland) + "/delete";

        assertThat(page(path).select("h1").text()).isEqualTo("Delete the link Iceland?");

        mockMvc.perform(post(path).with(menuAdministrator()).with(csrf()))
                .andExpect(redirectedUrl(MANAGE));

        assertThat(storedLinks.inMenu(TRAVEL)).isEmpty();
    }

    @Test
    void reorderingTheLinksPersistsTheNewOrder() throws Exception {
        MenuLink iceland = stored("Iceland", "/iceland", 0);
        MenuLink norway = stored("Norway", "/norway", 1);

        mockMvc.perform(post(MANAGE)
                        .param(MenuController.WEIGHT_PREFIX + iceland.id(), "10")
                        .param(MenuController.ENABLED_PREFIX + iceland.id(), "on")
                        .param(MenuController.WEIGHT_PREFIX + norway.id(), "-10")
                        .param(MenuController.ENABLED_PREFIX + norway.id(), "on")
                        .with(menuAdministrator())
                        .with(csrf()))
                .andExpect(redirectedUrl(MANAGE));

        assertThat(page(MANAGE).select("tbody tr td:first-child")).extracting(cell -> cell.text())
                .containsExactly("Norway", "Iceland");
    }

    @Test
    void aLinkLeftUntickedInTheOrderingTableIsTurnedOff() throws Exception {
        MenuLink iceland = stored("Iceland", "/iceland", 0);

        mockMvc.perform(post(MANAGE)
                        .param(MenuController.WEIGHT_PREFIX + iceland.id(), "")
                        .with(menuAdministrator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(storedLinks.find(entityIdOf(iceland)))
                .hasValueSatisfying(link -> {
                    assertThat(link.enabled()).isFalse();
                    assertThat(link.weight()).isZero();
                });
    }

    @Test
    void aLinkWhoseWeightWasNotSubmittedKeepsItsPlaceAtZero() throws Exception {
        MenuLink iceland = stored("Iceland", "/iceland", 7);

        mockMvc.perform(post(MANAGE)
                        .param(MenuController.ENABLED_PREFIX + iceland.id(), "on")
                        .with(menuAdministrator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(storedLinks.find(entityIdOf(iceland)))
                .hasValueSatisfying(link -> {
                    assertThat(link.weight()).isZero();
                    assertThat(link.enabled()).isTrue();
                });
    }

    @Test
    void theEditFormOfANestedLinkOpensWithItsParentSelected() throws Exception {
        MenuLink europe = stored("Europe", "/europe", 0);
        MenuLink norway = storedLinks.save(
                new MenuLink(null, TRAVEL, "Norway", "", "/norway", europe.id(), 0, false, true, null));

        Document document = page(MenuController.PATH + "/link/" + entityIdOf(norway) + "/edit");

        assertThat(document.selectFirst(
                        "select[name=" + MenuController.PARENT + "] option[selected]").attr("value"))
                .isEqualTo(europe.id());
    }

    @Test
    void reorderingLeavesTheLinksAModuleDeclaredAlone() throws Exception {
        mockMvc.perform(post(MenuController.PATH + "/manage/" + MenuConfig.MAIN)
                        .with(menuAdministrator())
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(page(MenuController.PATH + "/manage/" + MenuConfig.MAIN)
                .select("tbody tr td:first-child"))
                .extracting(cell -> cell.text())
                .contains("Home");
    }

    @Test
    void theOrderingTableOffersEveryStoredLinkForDragging() throws Exception {
        stored("Iceland", "/iceland", 0);

        Document document = page(MANAGE);

        assertThat(document.select("tr[data-drag-handle]")).hasSize(1);
        assertThat(document.select("table[data-drag-order]")).hasSize(1);
    }

    @Test
    void theLinkAdminIndentsALinkByHowDeepItHangs() throws Exception {
        MenuLink europe = stored("Europe", "/europe", 0);
        storedLinks.save(new MenuLink(null, TRAVEL, "Norway", "", "/norway", europe.id(), 0, false, true,
                null));

        assertThat(page(MANAGE).select("tbody tr td:first-child span"))
                .extracting(cell -> cell.attr("style"))
                .containsExactly("padding-left: 0rem", "padding-left: 1rem");
    }

    @Test
    void aMenuLinkRowIndentsItsTitleByItsDepth() {
        assertThat(new MenuLinkRow("id", "Norway", "/norway", 2, 0, true, true).indent())
                .isEqualTo("    ");
    }

    @Test
    void everyCoreMenuLinkNamesAMenuTheSiteHas() {
        List<String> menuIds = menus.all().stream().map(MenuConfig::id).toList();

        assertThat(new CoreMenuLinks().menuLinks()).allSatisfy(link ->
                assertThat(menuIds).contains(link.menu()));
    }
}
