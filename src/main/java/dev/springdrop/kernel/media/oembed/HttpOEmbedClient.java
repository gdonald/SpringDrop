package dev.springdrop.kernel.media.oembed;

import dev.springdrop.kernel.media.MediaSourceException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Asks YouTube and Vimeo over HTTP. Redirects are not followed, a request gives
 * up after ten seconds, and a thumbnail larger than five megabytes is refused.
 */
@Component
public class HttpOEmbedClient implements OEmbedClient {

    public static final List<OEmbedProvider> DEFAULT_PROVIDERS = List.of(
            new OEmbedProvider("YouTube",
                    "https://(www\\.)?(youtube\\.com/watch\\?v=|youtu\\.be/)[\\w-]{6,20}([&?#][^\\s]*)?",
                    "https://www.youtube.com/oembed?format=json&url=", List.of("i.ytimg.com")),
            new OEmbedProvider("Vimeo", "https://(www\\.)?vimeo\\.com/\\d{1,15}([/?#][^\\s]*)?",
                    "https://vimeo.com/api/oembed.json?url=", List.of("i.vimeocdn.com")));

    static final Duration TIMEOUT = Duration.ofSeconds(10);

    static final int MAX_THUMBNAIL_BYTES = 5 * 1024 * 1024;

    private final List<OEmbedProvider> providers;
    private final HttpClient http;
    private final ObjectMapper objectMapper;

    @Autowired
    public HttpOEmbedClient(ObjectMapper objectMapper) {
        this(DEFAULT_PROVIDERS, objectMapper);
    }

    public HttpOEmbedClient(List<OEmbedProvider> providers, ObjectMapper objectMapper) {
        this.providers = List.copyOf(providers);
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<OEmbedProvider> provider(String address) {
        return providers.stream().filter(provider -> provider.matches(address)).findFirst();
    }

    @Override
    public OEmbedResource fetch(String address) {
        OEmbedProvider provider = provider(address).orElseThrow(() ->
                new MediaSourceException("The address is not from a site the media library embeds from."));
        byte[] body = get(URI.create(provider.endpoint() + URLEncoder.encode(address, StandardCharsets.UTF_8)),
                "The site could not describe this address.");
        JsonNode resource = objectMapper.readTree(body);
        return new OEmbedResource(text(resource, "type"), text(resource, "title"), text(resource, "provider_name"),
                text(resource, "html"), text(resource, "thumbnail_url"), resource.path("width").asInt(0),
                resource.path("height").asInt(0));
    }

    @Override
    public byte[] thumbnail(OEmbedProvider provider, String thumbnailUrl) {
        URI address = URI.create(thumbnailUrl);
        if (!List.of("http", "https").contains(String.valueOf(address.getScheme()))
                || !provider.thumbnailHosts().contains(String.valueOf(address.getHost()))) {
            throw new MediaSourceException("The thumbnail is not on a host of " + provider.name() + ".");
        }
        return get(address, "The thumbnail could not be downloaded.");
    }

    private byte[] get(URI address, String failure) {
        try {
            HttpResponse<InputStream> response = http.send(HttpRequest.newBuilder(address).timeout(TIMEOUT).GET()
                    .build(), HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                byte[] read = body.readNBytes(MAX_THUMBNAIL_BYTES + 1);
                if (response.statusCode() != 200 || read.length > MAX_THUMBNAIL_BYTES) {
                    throw new MediaSourceException(failure);
                }
                return read;
            }
        } catch (IOException unreachable) {
            throw new MediaSourceException(failure);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new MediaSourceException(failure);
        }
    }

    private static String text(JsonNode resource, String name) {
        JsonNode value = resource.path(name);
        return value.isValueNode() ? value.asString() : "";
    }
}
