package dev.springdrop.kernel.theme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class ThemeRegistryTest {

    private static final String CONTENT = "content";

    private ThemeRegistry registry;

    @BeforeEach
    void buildRegistry() {
        registry = new ThemeRegistry(
                new DefaultResourceLoader(),
                List.of(Theme.named("vista"), Theme.extending("vista-child", "vista")),
                "classpath:/templates/",
                ".html",
                "");
    }

    @Test
    void aThemeIsFoundByTheNameItRegisteredUnder() {
        assertThat(registry.find("vista")).contains(Theme.named("vista"));
    }

    @Test
    void aNameNoThemeRegisteredUnderIsNotFound() {
        assertThat(registry.find("absent")).isEmpty();
    }

    @Test
    void theRegistryListsEveryRegisteredTheme() {
        assertThat(registry.names()).containsExactlyInAnyOrder("vista", "vista-child");
    }

    @Test
    void aThemeRegisteredAfterConstructionJoinsTheOthers() {
        registry.register(Theme.named("sparse"));

        assertThat(registry.names()).contains("sparse");
    }

    @Test
    void noThemeIsActiveUntilOneIsActivated() {
        assertThat(registry.active()).isEmpty();
    }

    @Test
    void activatingAThemeMakesItTheActiveOne() {
        registry.activate("vista");

        assertThat(registry.active()).contains("vista");
    }

    @Test
    void activatingAThemeThatIsNotRegisteredIsRejected() {
        assertThatThrownBy(() -> registry.activate("absent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absent");
    }

    @Test
    void deactivatingLeavesTheCoreTemplatesInCharge() {
        registry.activate("vista");
        registry.deactivate();

        assertThat(registry.active()).isEmpty();
        assertThat(registry.chain()).containsExactly("");
    }

    @Test
    void theChainOfAThemeWithAParentRunsChildThenParentThenCore() {
        assertThat(registry.chainFor("vista-child"))
                .containsExactly("themes/vista-child", "themes/vista", "");
    }

    @Test
    void theChainOfAnUnregisteredThemeIsTheCoreTemplatesAlone() {
        assertThat(registry.chainFor("absent")).containsExactly("");
    }

    @Test
    void aThemeInheritanceCycleStopsRatherThanLooping() {
        registry.register(new Theme("ouroboros", java.util.Optional.of("tail"), "themes/ouroboros"));
        registry.register(new Theme("tail", java.util.Optional.of("ouroboros"), "themes/tail"));

        assertThat(registry.chainFor("ouroboros"))
                .containsExactly("themes/ouroboros", "themes/tail", "");
    }

    @Test
    void theMostSpecificSuggestionWinsEvenWhenOnlyTheCoreTemplatesProvideIt() {
        registry.activate("vista");

        assertThat(registry.resolve(CONTENT, List.of("node--teaser", "node")))
                .contains("content/node--teaser");
    }

    @Test
    void theActiveThemeWinsAmongTemplatesOfTheSameSuggestion() {
        registry.activate("vista");

        assertThat(registry.resolve(CONTENT, List.of("node"))).contains("themes/vista/content/node");
    }

    @Test
    void aChildThemeWinsOverTheParentItInherits() {
        registry.activate("vista-child");

        assertThat(registry.resolve(CONTENT, List.of("node")))
                .contains("themes/vista-child/content/node");
    }

    @Test
    void aParentTemplateIsUsedWhereTheChildProvidesNone() {
        registry.activate("vista-child");

        assertThat(registry.resolve(CONTENT, List.of("node--article")))
                .contains("themes/vista/content/node--article");
    }

    @Test
    void suggestionsNoTemplateExistsForResolveToNothing() {
        assertThat(registry.resolve(CONTENT, List.of("node--missing"))).isEmpty();
    }

    @Test
    void aConfiguredDefaultThemeIsActiveFromTheStart() {
        ThemeRegistry configured = new ThemeRegistry(
                new DefaultResourceLoader(),
                List.of(Theme.named("vista")),
                "classpath:/templates/",
                ".html",
                "vista");

        assertThat(configured.active()).contains("vista");
    }

    @Test
    void aDefaultThemeThatIsNotRegisteredIsRejectedAtStartup() {
        assertThatThrownBy(() -> new ThemeRegistry(
                new DefaultResourceLoader(),
                List.of(Theme.named("vista")),
                "classpath:/templates/",
                ".html",
                "absent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absent");
    }

    @Test
    void aThemeActivatedForThisRequestOutranksTheSiteDefault() {
        ThemeRegistry configured = new ThemeRegistry(
                new DefaultResourceLoader(),
                List.of(Theme.named("vista"), Theme.extending("vista-child", "vista")),
                "classpath:/templates/",
                ".html",
                "vista");

        configured.activate("vista-child");
        assertThat(configured.active()).contains("vista-child");

        configured.deactivate();
        assertThat(configured.active()).contains("vista");
    }

    @Test
    void aThemeNeedsAName() {
        assertThatThrownBy(() -> Theme.named(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void aThemeWithANullNameIsRejected() {
        assertThatThrownBy(() -> Theme.named(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
