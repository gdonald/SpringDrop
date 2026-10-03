package dev.springdrop.kernel.media.oembed;

import dev.springdrop.kernel.media.MediaSourceException;
import java.util.Optional;

/** Asks the providers the site embeds from about their media. */
public interface OEmbedClient {

    /** The provider an address belongs to, or nothing for a site the media library does not embed from. */
    Optional<OEmbedProvider> provider(String address);

    /**
     * What the address's provider says about it.
     *
     * @throws MediaSourceException when no provider takes the address or the provider cannot describe it
     */
    OEmbedResource fetch(String address);

    /**
     * The thumbnail image a provider described.
     *
     * @throws MediaSourceException when the thumbnail is not on one of the provider's hosts or cannot be downloaded
     */
    byte[] thumbnail(OEmbedProvider provider, String thumbnailUrl);
}
