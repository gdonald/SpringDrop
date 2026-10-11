package dev.springdrop.kernel.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.views.DefaultViews;
import dev.springdrop.kernel.views.ViewConfig;
import dev.springdrop.kernel.views.ViewManager;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.context.request.RequestContextHolder;

@SpringBootTest
class ConfigTranslationManagerIntegrationTest extends AbstractIntegrationTest {

    private static final String CONTENT_VIEW = ViewConfig.configName(DefaultViews.CONTENT);

    @Autowired
    private ConfigTranslationManager translations;

    @Autowired
    private ConfigStore config;

    @Autowired
    private ViewManager views;

    @Autowired
    private DefaultViews defaultViews;

    @BeforeEach
    void theDefaultViews() {
        clear();
        views.delete(DefaultViews.CONTENT);
        defaultViews.install();
    }

    @AfterEach
    void nothingLeft() {
        clear();
        LocaleContextHolder.resetLocaleContext();
    }

    private void clear() {
        config.listNames(ConfigTranslation.CONFIG_PREFIX).forEach(config::delete);
        config.delete("test.mapped.thing");
    }

    @Test
    void aViewsDisplayTitlesAreTranslatedByDisplay() {
        assertThat(translations.sourceStrings(CONTENT_VIEW)).extracting(ConfigTranslationManager.SourceString::path)
                .containsExactly("label", "description", "displays.default.title", "displays.page_1.title");

        translations.save("fr", CONTENT_VIEW, Map.of("label", "Contenu", "displays.page_1.title", "Tout le contenu"));
        RequestContextHolder.resetRequestAttributes();
        LocaleContextHolder.setLocale(Locale.FRENCH);

        ViewConfig french = views.find(DefaultViews.CONTENT).orElseThrow();
        assertThat(french.label()).isEqualTo("Contenu");
        assertThat(french.display("page_1").orElseThrow().title()).isEqualTo("Tout le contenu");
        assertThat(french.display("default").orElseThrow().title()).isEqualTo("Content");

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(views.find(DefaultViews.CONTENT).orElseThrow().label()).isEqualTo("Content");
    }

    @Test
    void aTranslationIntoAListThatIsNotThereChangesNothingInIt() {
        translations.save("fr", CONTENT_VIEW, Map.of("label.x.title", "Nothing"));

        assertThat(translations.overrides("fr", CONTENT_VIEW)).hasValueSatisfying(overrides ->
                assertThat(overrides).containsEntry("label", "Content"));
    }

    @Test
    void anObjectWithoutATranslationOrThatIsNotStoredHasNoOverrides() {
        assertThat(translations.overrides("fr", CONTENT_VIEW)).isEmpty();
        translations.save("fr", "views.view.gone", Map.of("label", "Parti"));
        assertThat(translations.overrides("fr", "views.view.gone")).isEmpty();
    }

    @Test
    void anObjectNobodyMapsCannotBeTranslatedAndAMissingOneHasNoStrings() {
        assertThatThrownBy(() -> translations.sourceStrings("image.style.thumbnail"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> translations.sourceStrings("views.view.gone"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(translations.mapperFor("system.site")).map(ConfigTranslationMapper::typeLabel)
                .contains("Site information");
        assertThat(translations.mapperFor("system.sitemap")).isEmpty();
    }

    @Test
    void anObjectWithoutALabelIsListedByItsNameAndItsEmptyStringsAreLeftOut() {
        config.save("system.site", Map.of("name", "City library", "slogan", ""));

        assertThat(translations.items()).filteredOn(item -> item.name().equals("system.site"))
                .singleElement().satisfies(item -> assertThat(item.label()).isEqualTo("system.site"));
        assertThat(translations.sourceStrings("system.site")).extracting(ConfigTranslationManager.SourceString::path)
                .containsExactly("name");
        config.delete("system.site");
    }

    @Test
    void listItemsWithoutTheStringOrWithoutAnIdAreLeftAlone() {
        config.save(ViewConfig.configName("odd"), Map.of("id", "odd", "label", "Odd", "displays", List.of(
                Map.of("id", "a"), Map.of("id", "b", "title", ""), Map.of("id", "c", "title", "Third"),
                Map.of("title", "No id"))));

        assertThat(translations.sourceStrings(ViewConfig.configName("odd")))
                .extracting(ConfigTranslationManager.SourceString::path)
                .containsExactly("label", "displays.c.title");

        translations.save("fr", ViewConfig.configName("odd"), Map.of("displays.c.title", "Troisième"));
        assertThat(translations.overrides("fr", ViewConfig.configName("odd"))).hasValueSatisfying(overrides ->
                assertThat(String.valueOf(overrides.get("displays"))).contains("Troisième", "No id"));
        config.delete(ViewConfig.configName("odd"));
    }

    @Test
    void aTranslationIsReadAsStoredInAnyLanguage() {
        translations.save("fr", CONTENT_VIEW, Map.of("label", "Contenu"));
        RequestContextHolder.resetRequestAttributes();
        LocaleContextHolder.setLocale(Locale.FRENCH);

        assertThat(config.read(ConfigTranslation.configName("fr", CONTENT_VIEW), ConfigTranslation.class, null)
                .values()).containsEntry("label", "Contenu");
    }

    @Test
    void aListWithoutItsKeyHasNothingToTranslate() {
        views.save(new ViewConfig("bare", "Bare", "", "node", List.of()));
        config.save(ViewConfig.configName("bare"), Map.of("id", "bare", "label", "Bare"));

        assertThat(translations.sourceStrings(ViewConfig.configName("bare")))
                .extracting(ConfigTranslationManager.SourceString::path).containsExactly("label");
        views.delete("bare");
    }
}
