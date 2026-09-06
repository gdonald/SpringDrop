package dev.springdrop.kernel.mail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.kernel.token.TokenContext;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MailManagerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MailManager mailManager;

    @Autowired
    private CollectingMailBackend backend;

    @Autowired
    private ConfigStore configStore;

    @BeforeEach
    void namedSiteAndEmptyMailbox() {
        backend.clear();
        configStore.save(SiteInformation.CONFIG_NAME, new SiteInformation(
                "My Site", "", "site@example.com", "https://example.com"));
    }

    @AfterEach
    void restoreDefaultSiteInformation() {
        configStore.save(SiteInformation.CONFIG_NAME, SiteInformation.DEFAULTS);
    }

    @Test
    void rendersTokensAndDeliversThroughTheBackend() {
        mailManager.send(
                "user@example.com",
                "Welcome to [site:name]",
                "Thanks for joining [site:name].",
                TokenContext.of(Map.of()));

        assertThat(backend.delivered()).singleElement().satisfies(message -> {
            assertThat(message.to()).isEqualTo("user@example.com");
            assertThat(message.subject()).isEqualTo("Welcome to My Site");
            assertThat(message.body()).isEqualTo("Thanks for joining My Site.");
        });
    }
}
