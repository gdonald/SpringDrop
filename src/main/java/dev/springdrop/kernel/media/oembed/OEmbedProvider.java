package dev.springdrop.kernel.media.oembed;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A site the media library embeds from: the addresses of its media, the oEmbed
 * endpoint describing one, and the hosts its thumbnails are served from, which
 * are the only ones a thumbnail is downloaded from.
 */
public record OEmbedProvider(String name, String addressPattern, String endpoint, List<String> thumbnailHosts) {

    public OEmbedProvider {
        thumbnailHosts = List.copyOf(thumbnailHosts);
    }

    public boolean matches(String address) {
        return Pattern.compile(addressPattern).matcher(address).matches();
    }
}
