package dev.springdrop.kernel.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.render.RenderService;
import dev.springdrop.kernel.render.RenderedPage;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;

@SpringBootTest
@Import(ThemeLayerIntegrationTest.PreprocessListeners.class)
class ThemeLayerIntegrationTest extends AbstractIntegrationTest {

    private static final String CONTENT = "content";

    @Autowired
    private ThemeRegistry registry;

    @Autowired
    private ThemeService themes;

    @Autowired
    private RenderService renderer;

    @Autowired
    private RecordingPreprocessor preprocessor;

    @BeforeEach
    void registerThemes() {
        registry.register(Theme.named("vista"));
        registry.register(Theme.extending("vista-child", "vista"));
        registry.deactivate();
        preprocessor.hooks.clear();
    }

    @AfterEach
    void restoreTheDefaultTheme() {
        registry.activate(Theme.FRONT_END);
    }

    @Test
    void anEntityWithNoThemeActiveDrawsWithTheCoreTemplate() {
        Element article = render(themes.entity(
                CONTENT, "node", "article", null, "7", Map.of("title", "Board minutes", "body", "Approved")));

        assertThat(article.className()).isEqualTo("node");
        assertThat(article.selectFirst("h2").text()).isEqualTo("Board minutes");
    }

    @Test
    void aBundleOverrideInAThemeChangesThatBundleAlone() {
        registry.activate("vista");

        Element article = render(themes.entity(
                CONTENT, "node", "article", null, "7", Map.of("title", "Board minutes")));
        Element page = render(themes.entity(
                CONTENT, "node", "page", null, "8", Map.of("title", "Contact us")));

        assertThat(article.className()).isEqualTo("node node-article");
        assertThat(page.className()).isEqualTo("node node-vista");
    }

    @Test
    void aCoreViewModeTemplateOutranksTheThemesLessSpecificOne() {
        registry.activate("vista");

        Element teaser = render(themes.entity(
                CONTENT, "node", "page", "teaser", "8", Map.of("title", "Contact us")));

        assertThat(teaser.className()).isEqualTo("node node-teaser");
        assertThat(teaser.selectFirst("h3").text()).isEqualTo("Contact us");
    }

    @Test
    void preprocessingAddsVariablesTheChosenTemplateDraws() {
        registry.activate("vista");

        Element article = render(themes.entity(
                CONTENT, "node", "article", null, "7", Map.of("title", "Board minutes")));

        assertThat(article.selectFirst(".byline").text()).isEqualTo("Posted by edith");
    }

    @Test
    void preprocessingSeesTheHookTheTemplateThatWonAndEverySuggestion() {
        registry.activate("vista");

        themes.entity(CONTENT, "node", "article", null, "7", Map.of("title", "Board minutes"));

        ThemePreprocessEvent event = preprocessor.hooks.getFirst();
        assertThat(event.hook()).isEqualTo("node");
        assertThat(event.template()).isEqualTo("themes/vista/content/node--article");
        assertThat(event.suggestions())
                .containsExactly("node--7", "node--article", "node");
    }

    @Test
    void themingSomethingNoTemplateExistsForIsRejected() {
        assertThatThrownBy(() -> themes.build(CONTENT, List.of("widget"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("widget");
    }

    private Element render(Renderable node) {
        RenderedPage page = renderer.render(node);
        return Jsoup.parseBodyFragment(page.html()).selectFirst("article");
    }

    @TestConfiguration
    static class PreprocessListeners {

        @Bean
        RecordingPreprocessor recordingPreprocessor() {
            return new RecordingPreprocessor();
        }
    }

    /** Stands in for a theme's preprocessing: records the event and adds a variable. */
    static class RecordingPreprocessor {

        private final List<ThemePreprocessEvent> hooks = new ArrayList<>();

        @EventListener
        void addAByline(ThemePreprocessEvent event) {
            hooks.add(event);
            event.subject().put("byline", "Posted by edith");
        }
    }
}
