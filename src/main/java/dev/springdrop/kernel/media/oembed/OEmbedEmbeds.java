package dev.springdrop.kernel.media.oembed;

import dev.springdrop.kernel.security.ActionLinkTokenService;
import dev.springdrop.kernel.state.StateService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Embedding remote media. A provider's embed markup is not put into the site's
 * pages: it is served alone at {@code /media/oembed}, which a page frames, so
 * the markup runs apart from the page. The frame's address carries a hash
 * signed for the media address, so the endpoint only serves addresses the site
 * put on a page. What a provider says is kept for a day.
 */
@Component
public class OEmbedEmbeds {

    public static final String PATH = "/media/oembed";

    public static final String URL = "url";

    public static final String HASH = "hash";

    static final String CACHE_COLLECTION = "media.oembed";

    static final Duration CACHE_FOR = Duration.ofDays(1);

    private static final String TOKEN_IDENTITY = "oembed";

    private final ActionLinkTokenService tokens;
    private final OEmbedClient client;
    private final StateService state;

    public OEmbedEmbeds(ActionLinkTokenService tokens, OEmbedClient client, StateService state) {
        this.tokens = tokens;
        this.client = client;
        this.state = state;
    }

    public String frameAddress(String address) {
        return PATH + "?" + URL + "=" + URLEncoder.encode(address, StandardCharsets.UTF_8) + "&" + HASH + "="
                + hash(address);
    }

    public boolean validHash(String address, String hash) {
        return hash != null && MessageDigest.isEqual(hash.getBytes(StandardCharsets.UTF_8),
                hash(address).getBytes(StandardCharsets.UTF_8));
    }

    private String hash(String address) {
        return tokens.token(TOKEN_IDENTITY + ":" + address, TOKEN_IDENTITY);
    }

    /** What the provider says about the address, from the last day's answer when there is one. */
    public OEmbedResource resource(String address) {
        String key = hash(address);
        return state.getExpiring(CACHE_COLLECTION, key, OEmbedResource.class).orElseGet(() -> {
            OEmbedResource fetched = client.fetch(address);
            state.setWithExpiry(CACHE_COLLECTION, key, fetched, CACHE_FOR);
            return fetched;
        });
    }

    public OEmbedClient client() {
        return client;
    }
}
