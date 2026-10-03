package dev.springdrop.kernel.media.oembed;

/** What a provider says about one of its media: its kind, title, embed markup, thumbnail, and size. */
public record OEmbedResource(
        String type, String title, String providerName, String html, String thumbnailUrl, int width, int height) {
}
