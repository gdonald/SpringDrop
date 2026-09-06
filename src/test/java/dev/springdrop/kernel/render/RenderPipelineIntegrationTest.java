package dev.springdrop.kernel.render;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.support.AbstractIntegrationTest;
import java.time.Duration;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RenderPipelineIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RenderService renderer;

    @Test
    void aFragmentDrawsTheDataItWasGiven() {
        RenderedPage page = renderer.render(Renderable.of("text").with("value", "Board minutes"));

        assertThat(Jsoup.parseBodyFragment(page.html()).selectFirst("div").text())
                .isEqualTo("Board minutes");
    }

    @Test
    void aFragmentReadsTheAttributesTheRenderableCarries() {
        RenderedPage page = renderer.render(Renderable.of("text")
                .with("value", "Board minutes")
                .attribute("id", "minutes")
                .attribute("class", "lead"));

        Document document = Jsoup.parseBodyFragment(page.html());
        assertThat(document.selectFirst("#minutes").hasClass("lead")).isTrue();
    }

    @Test
    void aChildRendersInsideItsParent() {
        RenderedPage page = renderer.render(Renderable.of("list")
                .child(Renderable.of("listItem").child(Renderable.of("text").with("value", "One")))
                .child(Renderable.of("listItem").child(Renderable.of("text").with("value", "Two"))));

        assertThat(Jsoup.parseBodyFragment(page.html()).select("ul li div"))
                .extracting(element -> element.text())
                .containsExactly("One", "Two");
    }

    @Test
    void aChildsCacheTagsBubbleUpToThePage() {
        RenderedPage page = renderer.render(Renderable.of("container")
                .cacheTag("config:system.site")
                .child(Renderable.of("text").with("value", "Board minutes")
                        .cacheTag("node:7")
                        .cacheContext("user.permissions")));

        assertThat(page.cache().tags()).containsExactly("config:system.site", "node:7");
        assertThat(page.cache().contexts()).containsExactly("user.permissions");
    }

    @Test
    void thePageIsOnlyAsReusableAsItsLeastReusablePart() {
        RenderedPage page = renderer.render(Renderable.of("container")
                .maxAge(Duration.ofHours(1))
                .child(Renderable.of("text").with("value", "Now").maxAge(Duration.ZERO)));

        assertThat(page.cache().maxAge()).contains(Duration.ZERO);
        assertThat(page.cache().isCacheable()).isFalse();
    }

    @Test
    void whatTheChildrenAskThePageToCarryIsCarriedOnce() {
        RenderedPage page = renderer.render(Renderable.of("container")
                .styleSheet("/css/site.css")
                .child(Renderable.of("text").with("value", "One")
                        .styleSheet("/css/site.css")
                        .script("/js/form-states.js"))
                .child(Renderable.of("text").with("value", "Two")
                        .headTag("<meta name=\"robots\" content=\"noindex\">")));

        assertThat(page.attachments().styleSheets()).containsExactly("/css/site.css");
        assertThat(page.attachments().scripts()).containsExactly("/js/form-states.js");
        assertThat(page.attachments().headTags())
                .containsExactly("<meta name=\"robots\" content=\"noindex\">");
    }

    @Test
    void aPlaceholderIsReplacedOnceTheShellAroundItHasRendered() {
        RenderedPage page = renderer.render(Renderable.of("container")
                .cacheTag("config:system.site")
                .child(Renderable.lazy(() -> Renderable.of("text")
                        .with("value", "Signed in as edith")
                        .cacheContext("user")
                        .maxAge(Duration.ZERO))));

        assertThat(page.html()).doesNotContain(RenderService.PLACEHOLDER_ATTRIBUTE);
        assertThat(Jsoup.parseBodyFragment(page.html()).select("div div").text())
                .isEqualTo("Signed in as edith");
        assertThat(page.cache().tags()).containsExactly("config:system.site");
        assertThat(page.cache().contexts()).containsExactly("user");
        assertThat(page.cache().isCacheable()).isFalse();
    }

    @Test
    void aPlaceholderInsideAPlaceholderIsFilledInTurn() {
        RenderedPage page = renderer.render(Renderable.of("container")
                .child(Renderable.lazy(() -> Renderable.of("container")
                        .cacheTag("outer")
                        .child(Renderable.lazy(() -> Renderable.of("text")
                                .with("value", "Innermost")
                                .cacheTag("inner"))))));

        assertThat(page.html()).doesNotContain(RenderService.PLACEHOLDER_ATTRIBUTE);
        assertThat(Jsoup.parseBodyFragment(page.html()).text()).isEqualTo("Innermost");
        assertThat(page.cache().tags()).containsExactly("outer", "inner");
    }

    @Test
    void aFragmentFromAnotherTemplateDrawsTheNodeJustTheSame() {
        RenderedPage page = renderer.render(
                Renderable.of("render/test-elements", "badge").with("label", "Draft"));

        assertThat(Jsoup.parseBodyFragment(page.html()).selectFirst("span").text())
                .isEqualTo("Draft");
    }
}
