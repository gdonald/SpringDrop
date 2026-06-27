package dev.springdrop.kernel.mail;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.token.TokenContext;
import dev.springdrop.kernel.token.TokenProvider;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootTest
class MailManagerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MailManager mailManager;

    @Autowired
    private CollectingMailBackend backend;

    @BeforeEach
    void clear() {
        backend.clear();
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

    @TestConfiguration
    static class TestTokens {

        @Bean
        TokenProvider siteTokens() {
            return new TokenProvider() {
                @Override
                public String type() {
                    return "site";
                }

                @Override
                public String resolve(String name, TokenContext context) {
                    return "name".equals(name) ? "My Site" : null;
                }
            };
        }
    }
}
