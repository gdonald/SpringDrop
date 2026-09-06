package dev.springdrop.kernel.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.kernel.token.TokenContext;
import dev.springdrop.kernel.token.TokenDefinition;
import dev.springdrop.kernel.token.TokenProvider;
import dev.springdrop.kernel.token.TokenReplacer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

class MailManagerTest {

    private final CollectingMailBackend backend = new CollectingMailBackend();

    private final ConfigStore configStore = mock(ConfigStore.class);

    private final StaticMessageSource messageSource = new StaticMessageSource();

    private final TokenReplacer tokenReplacer = new TokenReplacer(List.of(new TokenProvider() {
        @Override
        public String type() {
            return "site";
        }

        @Override
        public String resolve(String name, TokenContext context) {
            return "name".equals(name) ? "<My Site>" : null;
        }

        @Override
        public List<TokenDefinition> availableTokens() {
            return List.of();
        }
    }));

    private final MailManager mailManager = new MailManager(
            backend,
            tokenReplacer,
            configStore,
            messageSource,
            List.of(new PlainTextMailFormatter(), new HtmlMailFormatter()));

    @BeforeEach
    void storedSiteAndMailSettings() {
        when(configStore.read(eq(SiteInformation.CONFIG_NAME), any(), any())).thenReturn(
                new SiteInformation("My Site", "", "site@example.com", "https://example.com"));
        when(configStore.read(eq(MailSettings.CONFIG_NAME), any(), any()))
                .thenReturn(new MailSettings("replies@example.com"));
    }

    private MailMessage sent() {
        return backend.delivered().getLast();
    }

    @Test
    void aPlainTextMailCarriesTheTokenValueAsWritten() {
        mailManager.send("user@example.com", "Welcome to [site:name]", "Hello from [site:name].",
                TokenContext.of(Map.of()));

        assertThat(sent().body()).isEqualTo("Hello from <My Site>.");
    }

    @Test
    void aPlainTextMailIsSentAsPlainText() {
        mailManager.send("user@example.com", "Subject", "Body", TokenContext.of(Map.of()));

        assertThat(sent().contentType()).isEqualTo("text/plain; charset=UTF-8");
    }

    @Test
    void anHtmlMailEscapesTokenValuesAndWrapsTheBody() {
        mailManager.send(new MailRequest("user@example.com", "Subject", "Hello from [site:name].",
                TokenContext.of(Map.of()), Locale.ENGLISH, HtmlMailFormatter.ID));

        assertThat(sent().body())
                .isEqualTo("<!DOCTYPE html><html><body>Hello from &lt;My Site&gt;.</body></html>");
        assertThat(sent().contentType()).isEqualTo("text/html; charset=UTF-8");
    }

    @Test
    void theSubjectComesFromTheMessageForTheRecipientsLanguage() {
        messageSource.addMessage("mail.welcome.subject", Locale.FRENCH, "Bienvenue sur [site:name]");

        mailManager.send(new MailRequest("user@example.com", "mail.welcome.subject", "Body",
                TokenContext.of(Map.of()), Locale.FRENCH, PlainTextMailFormatter.ID));

        assertThat(sent().subject()).isEqualTo("Bienvenue sur <My Site>");
    }

    @Test
    void aSubjectWithNoTranslationIsUsedAsWritten() {
        mailManager.send(new MailRequest("user@example.com", "Welcome", "Body",
                TokenContext.of(Map.of()), Locale.FRENCH, PlainTextMailFormatter.ID));

        assertThat(sent().subject()).isEqualTo("Welcome");
    }

    @Test
    void mailIsSentFromTheSiteMailAddressWithTheConfiguredReplyTo() {
        mailManager.send("user@example.com", "Subject", "Body", TokenContext.of(Map.of()));

        assertThat(sent().from()).isEqualTo("site@example.com");
        assertThat(sent().replyTo()).isEqualTo("replies@example.com");
    }

    @Test
    void anUnknownFormatIsRejected() {
        MailRequest request = new MailRequest("user@example.com", "Subject", "Body",
                TokenContext.of(Map.of()), Locale.ENGLISH, "carrier-pigeon");

        assertThatThrownBy(() -> mailManager.send(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("carrier-pigeon");
    }
}
