package dev.springdrop.kernel.menu;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.access.RouteAccessChecker;
import dev.springdrop.kernel.routing.RouteDefinition;
import dev.springdrop.kernel.routing.RouteRegistrar;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.theme.Link;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
class MenuIntegrationTest extends AbstractIntegrationTest {

    static final String GUIDE_PATH = "/guide";

    static final String LEDGER_PATH = "/ledger";

    static final String READ_THE_LEDGER = "read the ledger";

    private static final String TRAVEL = "travel";

    @Autowired
    private MenuManager menus;

    @Autowired
    private MenuLinkContentService storedLinks;

    @Autowired
    private MenuTreeBuilder trees;

    @Autowired
    private MenuNavigation navigation;

    @Autowired
    private BreadcrumbBuilder breadcrumbs;

    @BeforeEach
    void anEmptyTravelMenu() {
        storedLinks.install();
        menus.install();
        menus.save(MenuConfig.of(TRAVEL, "Travel", "Where the trips are written up."));
        clearLinks();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void theSiteAsItWas() {
        clearLinks();
        menus.delete(TRAVEL);
        SecurityContextHolder.clearContext();
    }

    private void clearLinks() {
        List.of(TRAVEL, MenuConfig.MAIN).forEach(menu ->
                storedLinks.inMenu(menu).forEach(link ->
                        MenuLinkContentService.entityId(link.id()).ifPresent(storedLinks::delete)));
    }

    private MenuLink stored(String title, String url) {
        return storedLinks.save(new MenuLink(null, TRAVEL, title, "", url, null, 0, false, true, null));
    }

    private void signedInWith(String... permissions) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("reader", "secret",
                        List.of(permissions).stream().map(SimpleGrantedAuthority::new).toList()));
    }

    @Test
    void aSiteStartsWithTheFourMenusItNavigatesBy() {
        assertThat(menus.all()).extracting(MenuConfig::id)
                .contains(MenuConfig.MAIN, MenuConfig.ADMIN, MenuConfig.FOOTER, MenuConfig.ACCOUNT);
    }

    @Test
    void installingTwiceLeavesTheMenusAsTheyWere() {
        MenuConfig asInstalled = menus.find(MenuConfig.MAIN).orElseThrow();
        menus.save(new MenuConfig(MenuConfig.MAIN, "Primary links", "Renamed.", true));

        menus.install();

        assertThat(menus.find(MenuConfig.MAIN))
                .hasValueSatisfying(menu -> assertThat(menu.label()).isEqualTo("Primary links"));
        menus.save(asInstalled);
    }

    @Test
    void theMenusTheSiteItselfHangsLinksInCannotBeDeleted() {
        assertThat(menus.delete(MenuConfig.MAIN)).isFalse();
        assertThat(menus.find(MenuConfig.MAIN)).isPresent();
    }

    @Test
    void aMenuTheSiteAddedCanBeDeleted() {
        assertThat(menus.delete(TRAVEL)).isTrue();
        assertThat(menus.find(TRAVEL)).isEmpty();
    }

    @Test
    void menusAreListedByName() {
        assertThat(menus.all()).extracting(MenuConfig::label).isSorted();
    }

    @Test
    void aStoredLinkRoundTripsEverythingItWasGiven() {
        MenuLink saved = storedLinks.save(new MenuLink(
                null, TRAVEL, "Iceland", "The ring road.", "/iceland", null, 3, true, true, null));

        assertThat(storedLinks.find(MenuLinkContentService.entityId(saved.id()).orElseThrow()))
                .hasValueSatisfying(link -> {
                    assertThat(link.title()).isEqualTo("Iceland");
                    assertThat(link.description()).isEqualTo("The ring road.");
                    assertThat(link.url()).isEqualTo("/iceland");
                    assertThat(link.weight()).isEqualTo(3);
                    assertThat(link.expanded()).isTrue();
                    assertThat(link.enabled()).isTrue();
                    assertThat(link.menu()).isEqualTo(TRAVEL);
                });
    }

    @Test
    void anIdThatNoStoredLinkOwnsBelongsToNoEntity() {
        assertThat(MenuLinkContentService.entityId("system.front_page")).isEmpty();
        assertThat(MenuLinkContentService.entityId(null)).isEmpty();
    }

    @Test
    void aStoredLinkIsGoneOnceItIsDeleted() {
        MenuLink saved = stored("Iceland", "/iceland");

        storedLinks.delete(MenuLinkContentService.entityId(saved.id()).orElseThrow());

        assertThat(storedLinks.inMenu(TRAVEL)).isEmpty();
    }

    @Test
    void aMenuWithNestedLinksComesOutInOrder() {
        MenuLink europe = storedLinks.save(
                new MenuLink(null, TRAVEL, "Europe", "", "/europe", null, 0, false, true, null));
        storedLinks.save(new MenuLink(null, TRAVEL, "Norway", "", "/norway", europe.id(), 5, false, true, null));
        storedLinks.save(new MenuLink(null, TRAVEL, "Iceland", "", "/iceland", europe.id(), -5, false, true,
                null));
        storedLinks.save(new MenuLink(null, TRAVEL, "Asia", "", "/asia", null, 10, false, true, null));

        MenuTree tree = trees.build(TRAVEL);

        assertThat(tree.items()).extracting(item -> item.link().title()).containsExactly("Europe", "Asia");
        assertThat(tree.items().getFirst().children()).extracting(item -> item.link().title())
                .containsExactly("Iceland", "Norway");
    }

    @Test
    void twoLinksOfEqualWeightComeOutByTitle() {
        stored("Norway", "/norway");
        stored("Iceland", "/iceland");

        assertThat(trees.build(TRAVEL).items()).extracting(item -> item.link().title())
                .containsExactly("Iceland", "Norway");
    }

    @Test
    void aLinkThatIsTurnedOffIsLeftOutOfTheTree() {
        storedLinks.save(new MenuLink(null, TRAVEL, "Iceland", "", "/iceland", null, 0, false, false, null));

        assertThat(trees.build(TRAVEL).isEmpty()).isTrue();
    }

    @Test
    void aLinkNamingAPermissionIsHiddenFromSomeoneWithoutIt() {
        assertThat(trees.build(MenuConfig.MAIN).items()).extracting(item -> item.link().title())
                .doesNotContain("The lockbox");

        signedInWith(READ_THE_LEDGER);

        assertThat(trees.build(MenuConfig.MAIN).items()).extracting(item -> item.link().title())
                .contains("The lockbox");
    }

    @Test
    void aLinkPointingAtAPageThePersonMayNotOpenIsHidden() {
        assertThat(trees.build(MenuConfig.MAIN).items()).extracting(item -> item.link().title())
                .doesNotContain("The ledger");

        signedInWith(READ_THE_LEDGER);

        assertThat(trees.build(MenuConfig.MAIN).items()).extracting(item -> item.link().title())
                .contains("The ledger");
    }

    @Test
    void theFirstAccountSeesEveryLinkWithoutHoldingAnyPermission() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        new AccountPrincipal(1L, "root", "", true, List.of()), "secret", List.of()));

        assertThat(trees.build(MenuConfig.MAIN).items()).extracting(item -> item.link().title())
                .contains("The ledger", "The lockbox");
    }

    @Test
    void hidingALinkHidesEverythingUnderIt() {
        MenuLink ledger = storedLinks.save(new MenuLink(
                null, TRAVEL, "Ledger", "", LEDGER_PATH, null, 0, false, true, null));
        storedLinks.save(new MenuLink(
                null, TRAVEL, "Last year", "", "/ledger/last-year", ledger.id(), 0, false, true, null));

        assertThat(trees.build(TRAVEL).isEmpty()).isTrue();

        signedInWith(READ_THE_LEDGER);

        assertThat(trees.build(TRAVEL).items().getFirst().children())
                .extracting(item -> item.link().title()).containsExactly("Last year");
    }

    @Test
    void aLinkWhoseParentIsGoneHangsAtTheTopOfTheMenu() {
        storedLinks.save(new MenuLink(
                null, TRAVEL, "Norway", "", "/norway", "menu_link_content:9999", 0, false, true, null));

        assertThat(trees.build(TRAVEL).items()).extracting(item -> item.link().title())
                .containsExactly("Norway");
    }

    @Test
    void aLinkHungUnderItselfHangsAtTheTopOfTheMenu() {
        MenuLink loop = stored("Europe", "/europe");
        storedLinks.save(loop.under(loop.id()));

        assertThat(trees.build(TRAVEL).items()).extracting(item -> item.link().title())
                .containsExactly("Europe");
    }

    @Test
    void twoLinksHungUnderEachOtherBothHangAtTheTopOfTheMenu() {
        MenuLink europe = stored("Europe", "/europe");
        MenuLink asia = stored("Asia", "/asia");
        storedLinks.save(europe.under(asia.id()));
        storedLinks.save(asia.under(europe.id()));

        assertThat(trees.build(TRAVEL).items()).extracting(item -> item.link().title())
                .containsExactly("Asia", "Europe");
    }

    @Test
    void aLinkUnderAGapInTheMenuKeepsTheParentItStillHas() {
        MenuLink europe = storedLinks.save(new MenuLink(
                null, TRAVEL, "Europe", "", "/europe", "menu_link_content:9999", 0, false, true, null));
        storedLinks.save(new MenuLink(null, TRAVEL, "Norway", "", "/norway", europe.id(), 0, false, true,
                null));

        MenuTree tree = trees.build(TRAVEL);

        assertThat(tree.items()).extracting(item -> item.link().title()).containsExactly("Europe");
        assertThat(tree.items().getFirst().children()).extracting(item -> item.link().title())
                .containsExactly("Norway");
    }

    @Test
    void aLinkHungUnderARingOfLinksKeepsThatParent() {
        MenuLink europe = stored("Europe", "/europe");
        MenuLink asia = stored("Asia", "/asia");
        storedLinks.save(europe.under(asia.id()));
        storedLinks.save(asia.under(europe.id()));
        storedLinks.save(new MenuLink(null, TRAVEL, "Norway", "", "/norway", europe.id(), 0, false, true,
                null));

        MenuTree tree = trees.build(TRAVEL);

        assertThat(tree.items()).extracting(item -> item.link().title()).containsExactly("Asia", "Europe");
        assertThat(tree.items().getLast().children()).extracting(item -> item.link().title())
                .containsExactly("Norway");
    }

    @Test
    void theAdminsViewOfAMenuShowsTheLinksThatAreTurnedOff() {
        storedLinks.save(new MenuLink(null, TRAVEL, "Iceland", "", "/iceland", null, 0, false, false, null));

        assertThat(trees.buildForAdministration(TRAVEL).items())
                .extracting(item -> item.link().title()).containsExactly("Iceland");
    }

    @Test
    void aBuiltMenuSaysWhatInvalidatesItAndWhatItVariesBy() {
        MenuTree tree = trees.build(TRAVEL);

        assertThat(tree.menu()).isEqualTo(TRAVEL);
        assertThat(tree.cacheability().tags()).containsExactly(MenuConfig.cacheTag(TRAVEL));
        assertThat(tree.cacheability().contexts())
                .containsExactly(RouteAccessChecker.USER_PERMISSIONS_CONTEXT);
    }

    @Test
    void theTrailToAPageRunsFromTheTopOfTheMenuDownToIt() {
        MenuLink europe = storedLinks.save(
                new MenuLink(null, TRAVEL, "Europe", "", "/europe", null, 0, false, true, null));
        MenuLink norway = storedLinks.save(
                new MenuLink(null, TRAVEL, "Norway", "", "/norway", europe.id(), 0, false, true, null));

        assertThat(trees.activeTrail(TRAVEL, "/norway")).containsExactly(europe.id(), norway.id());
    }

    @Test
    void aPageInNoMenuIsOnNoTrail() {
        stored("Europe", "/europe");

        assertThat(trees.activeTrail(TRAVEL, "/nowhere")).isEmpty();
    }

    @Test
    void aMenuRenderedOutsideARequestMarksNoTrail() {
        stored("Europe", "/europe");

        assertThat(trees.build(TRAVEL, null).items().getFirst().inActiveTrail()).isFalse();
    }

    @Test
    void aLinkThatIsItsOwnParentDoesNotTrailForever() {
        MenuLink loop = stored("Europe", "/europe");
        storedLinks.save(loop.under(loop.id()));

        assertThat(trees.activeTrail(TRAVEL, "/europe")).containsExactly(loop.id());
    }

    @Test
    void everyLinkOnTheTrailIsMarkedAndOpen() {
        MenuLink europe = storedLinks.save(
                new MenuLink(null, TRAVEL, "Europe", "", "/europe", null, 0, false, true, null));
        storedLinks.save(new MenuLink(null, TRAVEL, "Norway", "", "/norway", europe.id(), 0, false, true, null));

        MenuTreeItem top = trees.build(TRAVEL, "/norway").items().getFirst();

        assertThat(top.inActiveTrail()).isTrue();
        assertThat(top.open()).isTrue();
        assertThat(top.children().getFirst().inActiveTrail()).isTrue();
    }

    @Test
    void aLinkMarkedToShowItsChildrenIsOpenWithoutBeingOnTheTrail() {
        MenuLink europe = storedLinks.save(
                new MenuLink(null, TRAVEL, "Europe", "", "/europe", null, 0, true, true, null));
        storedLinks.save(new MenuLink(null, TRAVEL, "Norway", "", "/norway", europe.id(), 0, false, true, null));

        MenuTreeItem top = trees.build(TRAVEL, "/elsewhere").items().getFirst();

        assertThat(top.inActiveTrail()).isFalse();
        assertThat(top.open()).isTrue();
    }

    @Test
    void aLinkThatIsNeitherOpenNorOnTheTrailStaysClosed() {
        stored("Europe", "/europe");

        assertThat(trees.build(TRAVEL, "/elsewhere").items().getFirst().open()).isFalse();
    }

    @Test
    void theMainMenuIsWhatTheThemeDrawsAcrossTheTopOfThePage() {
        assertThat(navigation.primary("/")).extracting(Link::label).containsExactly("Home");
        assertThat(navigation.primary("/").getFirst().active()).isTrue();
    }

    @Test
    void aNamedMenuComesOutAsLinksCarryingTheirChildren() {
        MenuLink europe = storedLinks.save(
                new MenuLink(null, TRAVEL, "Europe", "", "/europe", null, 0, false, true, null));
        storedLinks.save(new MenuLink(null, TRAVEL, "Norway", "", "/norway", europe.id(), 0, false, true, null));

        List<Link> links = navigation.of(TRAVEL, "/norway");

        assertThat(links).extracting(Link::label).containsExactly("Europe");
        assertThat(links.getFirst().children()).extracting(Link::url).containsExactly("/norway");
    }

    @Test
    void theFrontPageIsTheWholeTrailWhenItIsThePageBeingShown() {
        assertThat(breadcrumbs.build("/")).containsExactly(BreadcrumbBuilder.HOME);
    }

    @Test
    void aPageThatHangsInAMenuTakesItsTrailFromThatMenu() {
        MenuLink europe = storedLinks.save(
                new MenuLink(null, MenuConfig.MAIN, "Europe", "", "/europe", null, 0, false, true, null));
        storedLinks.save(new MenuLink(null, MenuConfig.MAIN, "Norway", "", GUIDE_PATH, europe.id(), 0, false,
                true, null));

        assertThat(breadcrumbs.build(GUIDE_PATH)).extracting(Link::label)
                .containsExactly("Home", "Europe", "Norway");
    }

    @Test
    void anAdminPageTakesItsTrailFromTheAdministrationMenu() {
        assertThat(BreadcrumbBuilder.menuFor("/admin/people")).isEqualTo(MenuConfig.ADMIN);
        assertThat(BreadcrumbBuilder.menuFor(GUIDE_PATH)).isEqualTo(MenuConfig.MAIN);
    }

    @Test
    void aPageInNoMenuIsNamedFromTheRoutesAlongItsPath() {
        assertThat(breadcrumbs.build("/admin/people/permissions")).extracting(Link::label)
                .containsExactly("Home", "People", "Permissions");
    }

    @Test
    void aPathWithNoRegisteredRoutesAlongItLeavesJustTheFrontPage() {
        assertThat(breadcrumbs.build("/nowhere/at/all")).containsExactly(BreadcrumbBuilder.HOME);
    }

    @TestConfiguration
    static class LedgerPages {

        @Bean
        RouteRegistrar ledgerRoutes() {
            return () -> List.of(
                    RouteDefinition.frontEnd(GUIDE_PATH, "guide", "Guide"),
                    RouteDefinition.admin(LEDGER_PATH, "ledger", "The ledger", READ_THE_LEDGER));
        }

        @Bean
        MenuLinkProvider ledgerMenuLinks() {
            return () -> List.of(
                    MenuLink.of("test.ledger", MenuConfig.MAIN, "The ledger", LEDGER_PATH),
                    MenuLink.of("test.lockbox", MenuConfig.MAIN, "The lockbox", "/lockbox")
                            .requiring(READ_THE_LEDGER));
        }
    }
}
