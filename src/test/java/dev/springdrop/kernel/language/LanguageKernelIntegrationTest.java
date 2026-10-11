package dev.springdrop.kernel.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.plugins.LanguageSwitcherBlock;
import dev.springdrop.kernel.cache.CacheContexts;
import dev.springdrop.kernel.cache.CacheTags;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

@SpringBootTest
class LanguageKernelIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private LanguageManager languages;

    @Autowired
    private LanguageNegotiator negotiator;

    @Autowired
    private LanguageLinks links;

    @Autowired
    private LanguageSwitcherBlock switcher;

    @Autowired
    private ConfigStore config;

    private final LanguageConfig french = new LanguageConfig("fr", "French", LanguageConfig.LTR, 1, "fr",
            "fr.example.com");

    private final LanguageConfig german = new LanguageConfig("de", "German", LanguageConfig.LTR, 2, "", "");

    @BeforeEach
    void englishAlone() {
        clear();
    }

    @AfterEach
    void englishAloneAgain() {
        clear();
        LocaleContextHolder.resetLocaleContext();
    }

    private void clear() {
        config.delete(LanguageSettings.CONFIG_NAME);
        config.delete(NegotiationSettings.CONFIG_NAME);
        List.of("fr", "de").forEach(languages::delete);
    }

    @Test
    void englishIsInstalledOnceAndTheDefaultCannotBeDeleted() {
        languages.install();
        languages.install();

        assertThat(languages.all()).extracting(LanguageConfig::id).containsExactly(LanguageManager.ENGLISH);
        assertThat(languages.delete(LanguageManager.ENGLISH)).isFalse();
        assertThat(languages.delete("xx")).isFalse();
    }

    @Test
    void theCurrentLanguageIsTheRequestsOrTheDefault() {
        languages.save(french);

        LocaleContextHolder.setLocale(Locale.FRENCH);
        assertThat(languages.current().id()).isEqualTo("fr");

        LocaleContextHolder.setLocale(Locale.JAPANESE);
        assertThat(languages.current().id()).isEqualTo("en");

        languages.makeDefault("zz");
        assertThat(languages.current()).isEqualTo(new LanguageConfig("ja", "ja", LanguageConfig.LTR, 0, "", ""));
    }

    @Test
    void aLinkNamesTheLanguageByPrefixDomainOrParameter() {
        assertThat(links.url(french, "/node/4")).contains("/fr/node/4");
        assertThat(links.url(french, "/")).contains("/fr");
        assertThat(links.url(german, "/node/4")).contains("/node/4");

        negotiator.save(new NegotiationSettings(List.of(NegotiationSettings.URL), NegotiationSettings.DOMAIN));
        assertThat(links.url(french, "/node/4")).contains("//fr.example.com/node/4");
        assertThat(links.url(german, "/node/4")).contains("/node/4");

        negotiator.save(new NegotiationSettings(List.of(NegotiationSettings.SESSION), NegotiationSettings.PREFIX));
        assertThat(links.url(french, "/node/4")).contains("/node/4?language=fr");

        negotiator.save(new NegotiationSettings(List.of(NegotiationSettings.USER), NegotiationSettings.PREFIX));
        assertThat(links.url(french, "/node/4")).isEmpty();
    }

    @Test
    void theSwitcherLinksThePageInEachLanguageWithTheCurrentOneActive() {
        languages.save(french);
        LocaleContextHolder.setLocale(Locale.FRENCH);

        Renderable built = switcher.build(BlockContext.of("/node/4", "Spring"), Map.of()).orElseThrow();

        Document links = Jsoup.parse(String.valueOf(built.data().get("value")));
        assertThat(links.select("a").eachAttr("href")).containsExactly("/node/4", "/fr/node/4");
        assertThat(links.select("a.active").eachText()).containsExactly("French");
        assertThat(links.select("a").eachAttr("hreflang")).containsExactly("en", "fr");
        assertThat(switcher.label()).isEqualTo("Language switcher");
        assertThat(switcher.cacheability(Map.of()).contexts()).contains(CacheContexts.LANGUAGES,
                CacheContexts.URL_PATH);
        assertThat(switcher.cacheability(Map.of()).tags())
                .contains(CacheTags.configList(LanguageConfig.CONFIG_PREFIX));
    }

    @Test
    void theSwitcherShowsNothingForOneLanguageOrWithoutAMethodALinkCanUse() {
        assertThat(switcher.build(BlockContext.of("/node/4", "Spring"), Map.of())).isEmpty();

        languages.save(french);
        negotiator.save(new NegotiationSettings(List.of(NegotiationSettings.USER), NegotiationSettings.PREFIX));
        assertThat(switcher.build(BlockContext.of("/node/4", "Spring"), Map.of())).isEmpty();
    }

    @Test
    void theLocaleResolverReadsTheNegotiatedLanguageAndCannotBeSet() {
        NegotiatedLocaleResolver resolver = new NegotiatedLocaleResolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addPreferredLocale(Locale.GERMAN);

        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.GERMAN);
        request.setAttribute(LanguageNegotiationFilter.LOCALE_ATTRIBUTE, Locale.FRENCH);
        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.FRENCH);
        assertThatThrownBy(() -> resolver.setLocale(request, new MockHttpServletResponse(), Locale.ITALIAN))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void theNegotiatedRequestHasOnlyTheNegotiatedLocale() {
        LanguageNegotiationFilter.NegotiatedRequest request = new LanguageNegotiationFilter.NegotiatedRequest(
                new MockHttpServletRequest(), Locale.FRENCH);

        assertThat(request.getLocale()).isEqualTo(Locale.FRENCH);
        assertThat(Collections.list(request.getLocales())).containsExactly(Locale.FRENCH);
    }

    @Test
    void aRequestWithoutAnAccountOrAChosenSessionLanguageGetsTheDefault() {
        negotiator.save(new NegotiationSettings(List.of(NegotiationSettings.USER, NegotiationSettings.SESSION),
                NegotiationSettings.PREFIX));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());

        assertThat(negotiator.negotiate(request, null)).isEqualTo(LanguageManager.ENGLISH);
        assertThat(negotiator.negotiate(new MockHttpServletRequest(), null)).isEqualTo(LanguageManager.ENGLISH);
    }


    @Test
    void theUnprefixedRequestAnswersAsThePathWithoutThePrefix() {
        MockHttpServletRequest original = new MockHttpServletRequest("GET", "/fr/node/4");
        original.setServerName("example.com");
        LanguageUrlFilter.UnprefixedRequest request = new LanguageUrlFilter.UnprefixedRequest(original, "/node/4");

        assertThat(request.getRequestURI()).isEqualTo("/node/4");
        assertThat(request.getRequestURL().toString()).isEqualTo("http://example.com/node/4");
        assertThat(request.getServletPath()).isEqualTo("/node/4");
        assertThat(request.getPathInfo()).isNull();
    }
}
