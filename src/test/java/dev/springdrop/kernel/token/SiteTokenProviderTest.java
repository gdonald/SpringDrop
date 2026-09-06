package dev.springdrop.kernel.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.site.SiteInformation;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SiteTokenProviderTest {

    private final ConfigStore configStore = mock(ConfigStore.class);

    private final SiteTokenProvider provider = new SiteTokenProvider(configStore);

    @BeforeEach
    void storedSiteInformation() {
        when(configStore.read(eq(SiteInformation.CONFIG_NAME), any(), any()))
                .thenReturn(new SiteInformation("My Site", "Just landed", "site@example.com", "https://example.com"));
    }

    private String resolve(String name) {
        return provider.resolve(name, TokenContext.of(Map.of()));
    }

    @Test
    void resolvesTheSiteName() {
        assertThat(resolve("name")).isEqualTo("My Site");
    }

    @Test
    void resolvesTheSiteSlogan() {
        assertThat(resolve("slogan")).isEqualTo("Just landed");
    }

    @Test
    void resolvesTheSiteMailAddress() {
        assertThat(resolve("mail")).isEqualTo("site@example.com");
    }

    @Test
    void resolvesTheSiteUrl() {
        assertThat(resolve("url")).isEqualTo("https://example.com");
    }

    @Test
    void returnsNothingForAnUnknownSiteToken() {
        assertThat(resolve("motto")).isNull();
    }

    @Test
    void theTokenBrowserSeesEveryResolvableSiteToken() {
        assertThat(provider.availableTokens()).extracting(token -> token.name())
                .containsExactly("name", "slogan", "mail", "url");
    }

    @Test
    void theProviderAnswersToTheSiteType() {
        assertThat(provider.type()).isEqualTo("site");
    }
}
