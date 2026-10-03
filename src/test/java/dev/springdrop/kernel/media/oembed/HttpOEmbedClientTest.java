package dev.springdrop.kernel.media.oembed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import dev.springdrop.kernel.media.MediaSourceException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class HttpOEmbedClientTest {

    private HttpServer server;

    private String base;

    private OEmbedProvider provider;

    private HttpOEmbedClient client;

    @BeforeEach
    void aProviderOnThisMachine() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/oembed", exchange -> {
            String asked = URLDecoder.decode(exchange.getRequestURI().getRawQuery().substring("url=".length()),
                    StandardCharsets.UTF_8);
            String body = asked.endsWith("/1") ? "{\"type\":\"video\",\"title\":\"Opening night\","
                    + "\"provider_name\":\"Local\",\"html\":\"<iframe></iframe>\",\"thumbnail_url\":\"t.jpg\","
                    + "\"width\":480,\"height\":270}"
                    : "{\"type\":\"video\",\"title\":{\"nested\":true}}";
            int status = asked.endsWith("/404") ? 404 : 200;
            respond(exchange, status, body.getBytes(StandardCharsets.UTF_8));
        });
        server.createContext("/thumbnails/small.jpg", exchange -> respond(exchange, 200, new byte[] {1, 2, 3}));
        server.createContext("/thumbnails/huge.jpg", exchange ->
                respond(exchange, 200, new byte[HttpOEmbedClient.MAX_THUMBNAIL_BYTES + 1]));
        server.start();
        base = "http://localhost:" + server.getAddress().getPort();
        provider = new OEmbedProvider("Local", "https://videos\\.example\\.com/\\d+", base + "/oembed?url=",
                List.of("localhost"));
        client = new HttpOEmbedClient(List.of(provider), JsonMapper.builder().build());
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, byte[] body)
            throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @AfterEach
    void stopTheProvider() {
        server.stop(0);
        Thread.interrupted();
    }

    @Test
    void theDefaultProvidersTakeYouTubeAndVimeoAddressesAndNothingElse() {
        HttpOEmbedClient defaults = new HttpOEmbedClient(JsonMapper.builder().build());

        assertThat(defaults.provider("https://www.youtube.com/watch?v=dQw4w9WgXcQ").map(OEmbedProvider::name))
                .contains("YouTube");
        assertThat(defaults.provider("https://youtu.be/dQw4w9WgXcQ?t=42").map(OEmbedProvider::name))
                .contains("YouTube");
        assertThat(defaults.provider("https://vimeo.com/76979871").map(OEmbedProvider::name)).contains("Vimeo");
        assertThat(defaults.provider("https://example.com/watch?v=dQw4w9WgXcQ")).isEmpty();
        assertThat(defaults.provider("http://www.youtube.com/watch?v=dQw4w9WgXcQ")).isEmpty();
    }

    @Test
    void aProviderDescribesItsMedia() {
        OEmbedResource resource = client.fetch("https://videos.example.com/1");

        assertThat(resource).isEqualTo(new OEmbedResource("video", "Opening night", "Local", "<iframe></iframe>",
                "t.jpg", 480, 270));
    }

    @Test
    void partsThatAreNotPlainValuesReadAsEmpty() {
        OEmbedResource resource = client.fetch("https://videos.example.com/2");

        assertThat(List.of(resource.title(), resource.html(), String.valueOf(resource.width())))
                .containsExactly("", "", "0");
    }

    @Test
    void anAddressNoProviderTakesOrOneItCannotDescribeIsRefused() {
        assertThatThrownBy(() -> client.fetch("https://elsewhere.example.com/1"))
                .isInstanceOf(MediaSourceException.class)
                .hasMessage("The address is not from a site the media library embeds from.");
        assertThatThrownBy(() -> client.fetch("https://videos.example.com/404"))
                .hasMessage("The site could not describe this address.");
    }

    @Test
    void aThumbnailIsDownloadedOnlyFromTheProvidersHosts() {
        assertThat(client.thumbnail(provider, base + "/thumbnails/small.jpg")).containsExactly(1, 2, 3);
        assertThatThrownBy(() -> client.thumbnail(provider, "http://127.0.0.1:" + server.getAddress().getPort()
                + "/thumbnails/small.jpg")).hasMessage("The thumbnail is not on a host of Local.");
        assertThatThrownBy(() -> client.thumbnail(provider, "file://localhost/etc/passwd"))
                .hasMessage("The thumbnail is not on a host of Local.");
        assertThatThrownBy(() -> client.thumbnail(provider, "thumbnails/small.jpg"))
                .hasMessage("The thumbnail is not on a host of Local.");
    }

    @Test
    void aThumbnailLargerThanFiveMegabytesIsRefused() {
        assertThatThrownBy(() -> client.thumbnail(provider, base + "/thumbnails/huge.jpg"))
                .hasMessage("The thumbnail could not be downloaded.");
    }

    @Test
    void aProviderThatCannotBeReachedIsReportedAsUnableToDescribe() {
        server.stop(0);

        assertThatThrownBy(() -> client.fetch("https://videos.example.com/1"))
                .hasMessage("The site could not describe this address.");
    }

    @Test
    void anInterruptedRequestGivesUpAndKeepsTheInterruption() {
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> client.fetch("https://videos.example.com/1"))
                .hasMessage("The site could not describe this address.");
        assertThat(Thread.interrupted()).isTrue();
    }
}
