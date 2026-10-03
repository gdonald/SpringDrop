package dev.springdrop.kernel.media;

import dev.springdrop.kernel.media.oembed.HttpOEmbedClient;
import dev.springdrop.kernel.media.oembed.OEmbedClient;
import dev.springdrop.kernel.media.oembed.OEmbedProvider;
import dev.springdrop.kernel.media.oembed.OEmbedResource;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Answers for the real providers' addresses from what a test registered, counting the questions. */
public class StubOEmbedClient implements OEmbedClient {

    private final Map<String, OEmbedResource> resources = new LinkedHashMap<>();
    private final Map<String, byte[]> thumbnails = new LinkedHashMap<>();
    private int fetches;

    public void describe(String address, OEmbedResource resource) {
        resources.put(address, resource);
    }

    public void serveThumbnail(String url, byte[] image) {
        thumbnails.put(url, image);
    }

    public int fetches() {
        return fetches;
    }

    public void reset() {
        resources.clear();
        thumbnails.clear();
        fetches = 0;
    }

    @Override
    public Optional<OEmbedProvider> provider(String address) {
        return HttpOEmbedClient.DEFAULT_PROVIDERS.stream().filter(provider -> provider.matches(address)).findFirst();
    }

    @Override
    public OEmbedResource fetch(String address) {
        fetches++;
        if (provider(address).isEmpty()) {
            throw new MediaSourceException("The address is not from a site the media library embeds from.");
        }
        OEmbedResource resource = resources.get(address);
        if (resource == null) {
            throw new MediaSourceException("The site could not describe this address.");
        }
        return resource;
    }

    @Override
    public byte[] thumbnail(OEmbedProvider provider, String thumbnailUrl) {
        return thumbnails.get(thumbnailUrl);
    }
}
