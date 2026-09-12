package dev.springdrop.kernel.theme;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.support.AbstractIntegrationTest;
import dev.springdrop.support.BootstrapAssertions;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BaseThemeTest extends AbstractIntegrationTest {

    @Autowired
    private PageRenderer pages;

    @Autowired
    private ThemeService themes;

    @Test
    void theLayoutPutsTheContentInAMobileFirstGrid() {
        Document page = render(PageChrome.of("SpringDrop", "Board minutes"));

        assertThat(page.selectFirst("main.container")).isNotNull();
        assertThat(page.selectFirst("main .row > .col-12.col-lg-8"))
                .as("content sits in a column that is full width until the large breakpoint")
                .isNotNull();
        assertThat(page.selectFirst("main .col-12.col-lg-8 .card-text").text())
                .isEqualTo("Approved");
    }

    @Test
    void theNavbarCollapsesBehindAToggleOnSmallScreens() {
        Document page = render(PageChrome.of("SpringDrop", "Board minutes")
                .withPrimaryNavigation(List.of(new Link("Home", "/"), new Link("About", "/about"))));

        assertThat(page.selectFirst("nav.navbar").hasClass("navbar-expand-lg")).isTrue();
        assertThat(page.selectFirst("nav.navbar button.navbar-toggler").attr("data-bs-target"))
                .isEqualTo("#primary-navigation");
        assertThat(page.select("#primary-navigation .nav-link"))
                .extracting(element -> element.text())
                .containsExactly("Home", "About");
    }

    @Test
    void theTitleAndSloganHeadTheContentColumn() {
        Document page = render(
                PageChrome.of("SpringDrop", "Board minutes").withSlogan("Minutes of the board"));

        assertThat(page.selectFirst("main h1").text()).isEqualTo("Board minutes");
        assertThat(page.selectFirst("main header p").text()).isEqualTo("Minutes of the board");
        assertThat(page.selectFirst("title").text()).isEqualTo("Board minutes | SpringDrop");
    }

    @Test
    void aPageWithNoSloganHeadsTheColumnWithTheTitleAlone() {
        Document page = render(PageChrome.of("SpringDrop", "Board minutes"));

        assertThat(page.select("main header p")).isEmpty();
    }

    @Test
    void theBreadcrumbMarksItsLastCrumbAsWhereTheReaderIs() {
        Document page = render(PageChrome.of("SpringDrop", "Board minutes").withBreadcrumbs(
                List.of(new Link("Home", "/"), new Link("Minutes", "/minutes"))));

        assertThat(page.select("nav .breadcrumb .breadcrumb-item a"))
                .extracting(element -> element.text())
                .containsExactly("Home");
        assertThat(page.selectFirst(".breadcrumb-item.active").text()).isEqualTo("Minutes");
        assertThat(page.selectFirst(".breadcrumb-item.active").attr("aria-current")).isEqualTo("page");
    }

    @Test
    void theLocalTasksRenderAsBootstrapTabsWithTheCurrentOneActive() {
        Document page = render(PageChrome.of("SpringDrop", "Board minutes").withTabs(
                List.of(new Tab("View", "/minutes/1", true), new Tab("Edit", "/minutes/1/edit", false))));

        assertThat(page.selectFirst("ul.nav.nav-tabs")).isNotNull();
        assertThat(page.selectFirst(".nav-tabs .nav-link.active").text()).isEqualTo("View");
        assertThat(page.selectFirst(".nav-tabs .nav-link.active").attr("aria-current")).isEqualTo("page");
    }

    @Test
    void theLocalActionsRenderAsButtonsRatherThanOutlineButtons() {
        Document page = render(PageChrome.of("SpringDrop", "Board minutes")
                .withLocalActions(List.of(new Link("Edit", "/minutes/1/edit"))));

        BootstrapAssertions.assertEditControlsAreButtons(page);
        BootstrapAssertions.assertNoOutlineButtons(page);
    }

    @Test
    void eachMessageDrawsAsTheBootstrapAlertItsSeverityAsksFor() {
        Document page = render(PageChrome.of("SpringDrop", "Board minutes")
                .withMessage(StatusMessage.success("Saved."))
                .withMessage(StatusMessage.warning("Unpublished."))
                .withMessage(StatusMessage.error("Could not save."))
                .withMessage(StatusMessage.info("Two revisions.")));

        assertThat(page.select(".alert"))
                .extracting(element -> element.className() + ": " + element.text())
                .containsExactly(
                        "alert alert-success: Saved.",
                        "alert alert-warning: Unpublished.",
                        "alert alert-danger: Could not save.",
                        "alert alert-info: Two revisions.");
    }

    @Test
    void thePagerMarksThePageBeingReadAndDisablesTheEndItIsAt() {
        Document page = render(PageChrome.of("SpringDrop", "Minutes")
                .withPager(Pager.of(1, 3, number -> "/minutes?page=" + number)));

        assertThat(page.selectFirst("ul.pagination")).isNotNull();
        assertThat(page.selectFirst(".page-item.active .page-link").text()).isEqualTo("1");
        assertThat(page.select(".page-item.disabled .page-link"))
                .extracting(element -> element.text())
                .containsExactly("Previous");
        assertThat(page.select(".pagination .page-link").last().attr("href"))
                .isEqualTo("/minutes?page=2");
    }

    @Test
    void aPageThatAsksForNoChromeDrawsNoneOfIt() {
        Document page = render(PageChrome.of("SpringDrop", "Board minutes"));

        assertThat(page.select(".breadcrumb")).isEmpty();
        assertThat(page.select(".nav-tabs")).isEmpty();
        assertThat(page.select(".alert")).isEmpty();
        assertThat(page.select(".pagination")).isEmpty();
        assertThat(page.select("main .btn")).isEmpty();
    }

    private Document render(PageChrome chrome) {
        Renderable content = themes.build(
                "content", TemplateSuggestions.of("front-page"), Map.of("welcome", "Approved"));
        return Jsoup.parse(pages.render(chrome, content).html());
    }
}
