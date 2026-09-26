package dev.springdrop.kernel.block;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.access.RouteAccessChecker;
import dev.springdrop.kernel.block.content.BlockContentBlock;
import dev.springdrop.kernel.block.plugins.BreadcrumbBlock;
import dev.springdrop.kernel.block.plugins.CustomHtmlBlock;
import dev.springdrop.kernel.block.plugins.HelpBlock;
import dev.springdrop.kernel.block.plugins.MainContentBlock;
import dev.springdrop.kernel.block.plugins.MenuBlockDeriver;
import dev.springdrop.kernel.block.plugins.PageTitleBlock;
import dev.springdrop.kernel.block.plugins.PoweredByBlock;
import dev.springdrop.kernel.block.plugins.SiteBrandingBlock;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.menu.MenuConfig;
import dev.springdrop.kernel.menu.MenuLink;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.menu.MenuManager;
import dev.springdrop.kernel.render.CacheMetadata;
import dev.springdrop.kernel.render.RenderService;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.web.BlockLayoutController;
import dev.springdrop.web.CoreHelp;
import dev.springdrop.web.CoreMenuLinks;
import dev.springdrop.web.CustomBlockController;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BlockPluginIntegrationTest extends AbstractIntegrationTest {

    private static final String TRAVEL = "travel";

    @Autowired
    private BlockManager blocks;

    @Autowired
    private RenderService renderer;

    @Autowired
    private FormRenderer forms;

    @Autowired
    private MenuManager menus;

    @Autowired
    private MenuLinkContentService storedLinks;

    @Autowired
    private ConfigStore configStore;

    private SiteInformation siteAsItWas;

    @BeforeEach
    void theSiteAsInstalled() {
        menus.install();
        storedLinks.install();
        siteAsItWas = configStore.read(SiteInformation.CONFIG_NAME, SiteInformation.class, null);
    }

    @AfterEach
    void theSiteAsItWas() {
        menus.delete(TRAVEL);
        storedLinks.inMenu(MenuConfig.MAIN).forEach(link ->
                MenuLinkContentService.entityId(link.id()).ifPresent(storedLinks::delete));
        if (siteAsItWas == null) {
            configStore.delete(SiteInformation.CONFIG_NAME);
        } else {
            configStore.save(SiteInformation.CONFIG_NAME, siteAsItWas);
        }
    }

    private Optional<Document> build(String pluginId, BlockContext context, Map<String, Object> settings) {
        return blocks.plugin(pluginId).build(context, settings)
                .map(block -> Jsoup.parseBodyFragment(renderer.render(block).html()));
    }

    private Document built(String pluginId, BlockContext context) {
        return build(pluginId, context, Map.of()).orElseThrow();
    }

    @Test
    void theLibraryHoldsEverySystemBlockCoreShips() {
        assertThat(blocks.definitions()).extracting(BlockDefinition::id).contains(
                SiteBrandingBlock.ID,
                PageTitleBlock.ID,
                MenuBlockDeriver.PRIMARY,
                BreadcrumbBlock.ID,
                MainContentBlock.ID,
                HelpBlock.ID,
                PoweredByBlock.ID,
                CustomHtmlBlock.ID,
                BlockContentBlock.ID);
    }

    @Test
    void theLibraryListsItsBlocksByLabel() {
        assertThat(blocks.definitions()).extracting(BlockDefinition::label).isSorted();
    }

    @Test
    void thePrimaryMenuBlockIsTheOneDerivedForTheMainNavigation() {
        assertThat(blocks.plugin(MenuBlockDeriver.PRIMARY).label()).isEqualTo("Main navigation");
    }

    @Test
    void aMenuAddedAfterTheLibraryWasListedGetsABlockOfItsOwn() {
        blocks.definitions();

        menus.save(MenuConfig.of(TRAVEL, "Travel", "Where the trips are written up."));

        assertThat(blocks.has(MenuBlockDeriver.ID + ":" + TRAVEL)).isTrue();
    }

    @Test
    void aMenuDeletedTakesItsBlockWithIt() {
        menus.save(MenuConfig.of(TRAVEL, "Travel", "Where the trips are written up."));
        blocks.definitions();

        menus.delete(TRAVEL);

        assertThat(blocks.has(MenuBlockDeriver.ID + ":" + TRAVEL)).isFalse();
    }

    @Test
    void theBrandingBlockLinksTheSiteNameHomeAndShowsTheSlogan() {
        configStore.save(SiteInformation.CONFIG_NAME, new SiteInformation(
                "Parish council", "Minutes and notices", "clerk@example.com", "http://localhost:8080"));

        Document branding = built(SiteBrandingBlock.ID, BlockContext.of("/", "Home"));

        assertThat(branding.selectFirst("a[rel=home]").text()).isEqualTo("Parish council");
        assertThat(branding.selectFirst("a[rel=home]").attr("href")).isEqualTo("/");
        assertThat(branding.selectFirst("p").text()).isEqualTo("Minutes and notices");
    }

    @Test
    void theBrandingBlockOfASiteWithNoSloganShowsTheNameAlone() {
        configStore.delete(SiteInformation.CONFIG_NAME);

        Document branding = built(SiteBrandingBlock.ID, BlockContext.of("/", "Home"));

        assertThat(branding.select("p")).isEmpty();
    }

    @Test
    void theBrandingBlockIsInvalidatedWhenTheSiteInformationChanges() {
        assertThat(blocks.plugin(SiteBrandingBlock.ID).cacheability(Map.of()).tags())
                .containsExactly("config:" + SiteInformation.CONFIG_NAME);
    }

    @Test
    void thePageTitleBlockHeadsThePageWithItsTitle() {
        assertThat(built(PageTitleBlock.ID, BlockContext.of("/minutes", "Board minutes")).selectFirst("h1").text())
                .isEqualTo("Board minutes");
        assertThat(blocks.plugin(PageTitleBlock.ID).cacheability(Map.of()).contexts())
                .containsExactly("url.path");
    }

    @Test
    void aPageWithNoTitleHasNoTitleBlock() {
        assertThat(build(PageTitleBlock.ID, BlockContext.of("/minutes", " "), Map.of())).isEmpty();
    }

    @Test
    void thePrimaryMenuBlockDrawsTheMainMenuWithTheCurrentPageMarked() {
        Document menu = built(MenuBlockDeriver.PRIMARY, BlockContext.of("/", "Home"));

        assertThat(menu.selectFirst("nav").attr("aria-label")).isEqualTo("Main navigation");
        assertThat(menu.selectFirst("a.nav-link.active").text()).isEqualTo("Home");
    }

    @Test
    void aMenuBlockNestsTheLinksUnderTheOneTheyHangFrom() {
        storedLinks.save(new MenuLink(null, MenuConfig.MAIN, "Notices", "", "/notices",
                CoreMenuLinks.HOME, 0, false, true, null));

        Document menu = built(MenuBlockDeriver.PRIMARY, BlockContext.of("/notices", "Notices"));

        assertThat(menu.select("ul ul a").text()).isEqualTo("Notices");
        assertThat(menu.select("ul ul ul")).isEmpty();
    }

    @Test
    void aMenuWithNothingInItHasNoBlock() {
        menus.save(MenuConfig.of(TRAVEL, "Travel", "Where the trips are written up."));

        assertThat(build(MenuBlockDeriver.ID + ":" + TRAVEL, BlockContext.of("/", "Home"), Map.of())).isEmpty();
    }

    @Test
    void aMenuBlockVariesByPermissionsAndByPageAndIsInvalidatedWithItsMenu() {
        CacheMetadata cacheability = blocks.plugin(MenuBlockDeriver.PRIMARY).cacheability(Map.of());

        assertThat(cacheability.tags()).containsExactly(MenuConfig.cacheTag(MenuConfig.MAIN));
        assertThat(cacheability.contexts())
                .containsExactly(RouteAccessChecker.USER_PERMISSIONS_CONTEXT, "url.path");
    }

    @Test
    void theBreadcrumbBlockDrawsTheTrailToThePage() {
        Document trail = built(BreadcrumbBlock.ID, BlockContext.of("/admin/structure/block", "Block layout"));

        assertThat(trail.select(".breadcrumb-item a").first().text()).isEqualTo("Home");
        assertThat(trail.selectFirst(".breadcrumb-item.active").text()).isEqualTo("Block layout");
        assertThat(blocks.plugin(BreadcrumbBlock.ID).cacheability(Map.of()).contexts())
                .containsExactly("url.path", RouteAccessChecker.USER_PERMISSIONS_CONTEXT);
    }

    @Test
    void theMainContentBlockDrawsWhatTheControllerProduced() {
        BlockContext context = BlockContext.of("/minutes", "Board minutes")
                .withMainContent(Renderable.of("text").with("value", "Approved"));

        assertThat(built(MainContentBlock.ID, context).text()).isEqualTo("Approved");
    }

    @Test
    void aPageWithNoMainContentHasNoMainContentBlock() {
        assertThat(build(MainContentBlock.ID, BlockContext.of("/minutes", "Board minutes"), Map.of())).isEmpty();
    }

    @Test
    void theHelpBlockShowsTheHelpAModuleGivesForThePage() {
        assertThat(built(HelpBlock.ID, BlockContext.of(BlockLayoutController.PATH, "Block layout")).text())
                .isEqualTo(CoreHelp.BLOCK_LAYOUT);
        assertThat(built(HelpBlock.ID, BlockContext.of(CustomBlockController.PATH, "Library")).text())
                .isEqualTo(CoreHelp.CUSTOM_BLOCKS);
        assertThat(blocks.plugin(HelpBlock.ID).cacheability(Map.of()).contexts()).containsExactly("url.path");
    }

    @Test
    void aPageNoModuleGivesHelpForHasNoHelpBlock() {
        assertThat(build(HelpBlock.ID, BlockContext.of("/minutes", "Board minutes"), Map.of())).isEmpty();
    }

    @Test
    void thePoweredByBlockNamesThePlatformAndDependsOnNothing() {
        assertThat(built(PoweredByBlock.ID, BlockContext.of("/", "Home")).text())
                .isEqualTo("Powered by SpringDrop");
        assertThat(blocks.plugin(PoweredByBlock.ID).cacheability(Map.of())).isEqualTo(CacheMetadata.EMPTY);
    }

    @Test
    void aCustomHtmlBlockDrawsItsMarkupWithScriptsRemoved() {
        Document block = build(CustomHtmlBlock.ID, BlockContext.of("/", "Home"), Map.of(
                CustomHtmlBlock.BODY, "<p onclick=\"steal()\">Office closed Monday.</p><script>steal()</script>"))
                .orElseThrow();

        assertThat(block.selectFirst("p").text()).isEqualTo("Office closed Monday.");
        assertThat(block.selectFirst("p").hasAttr("onclick")).isFalse();
        assertThat(block.select("script")).isEmpty();
    }

    @Test
    void aCustomHtmlBlockWithNoBodyShowsNothing() {
        assertThat(build(CustomHtmlBlock.ID, BlockContext.of("/", "Home"), Map.of())).isEmpty();
    }

    @Test
    void aCustomHtmlBlockExposesItsBodyAsARequiredSetting() {
        List<FormElement> settings = blocks.plugin(CustomHtmlBlock.ID)
                .settingsForm(Map.of(CustomHtmlBlock.BODY, "<p>Office closed Monday.</p>"));

        Document form = Jsoup.parseBodyFragment(forms.render(container(settings)));
        assertThat(form.selectFirst("textarea[name=" + CustomHtmlBlock.BODY_ELEMENT + "]").text())
                .isEqualTo("<p>Office closed Monday.</p>");
        assertThat(form.selectFirst("textarea").hasAttr("required")).isTrue();
    }

    @Test
    void aCustomHtmlBlockReadsItsBodyBackFromASubmission() {
        assertThat(blocks.plugin(CustomHtmlBlock.ID).settingsValues(
                Map.of(CustomHtmlBlock.BODY_ELEMENT, "<p>Open</p>", "label", "Notice")))
                .containsExactly(Map.entry(CustomHtmlBlock.BODY, "<p>Open</p>"));
    }

    @Test
    void aBlockWithNoSettingsExposesAnEmptySettingsForm() {
        BlockPlugin branding = blocks.plugin(SiteBrandingBlock.ID);

        assertThat(branding.settingsForm(Map.of())).isEmpty();
        assertThat(branding.settingsValues(Map.of("settings_anything", "x"))).isEmpty();
        assertThat(branding.validateSettings(Map.of())).isEmpty();
    }

    private static FormElement container(List<FormElement> settings) {
        FormElement container = FormElement.of(ElementType.CONTAINER, "settings");
        settings.forEach(container::child);
        return container;
    }
}
