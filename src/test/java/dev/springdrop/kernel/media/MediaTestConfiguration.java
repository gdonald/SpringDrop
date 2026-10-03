package dev.springdrop.kernel.media;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Puts the stub in place of the HTTP oEmbed client, so media tests never leave the machine. */
@TestConfiguration
public class MediaTestConfiguration {

    @Bean
    @Primary
    public StubOEmbedClient stubOEmbedClient() {
        return new StubOEmbedClient();
    }
}
