package dev.springdrop.kernel.image;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.event.EntityEvent;
import dev.springdrop.kernel.file.FileEntityType;
import dev.springdrop.kernel.file.FileSchemes;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.ImageSize;
import dev.springdrop.kernel.file.ManagedFile;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.image.effects.ScaleAndCropEffect;
import dev.springdrop.kernel.image.effects.ScaleEffect;
import dev.springdrop.kernel.plugin.PluginManager;
import dev.springdrop.kernel.plugin.PluginRegistry;
import dev.springdrop.kernel.security.ActionLinkTokenService;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The site's image styles and the derivatives drawn with them.
 *
 * <p>A derivative of {@code <scheme>://<path>} drawn with a style is kept in the
 * same scheme at {@code styles/<style>/<scheme>/<path>}, so a private image's
 * derivatives stay private. It is drawn the first time it is asked for, kept,
 * and drawn again when the original is newer. Saving or deleting a style
 * removes every derivative drawn with it, and deleting a file removes its
 * derivatives of every style.
 *
 * <p>Drawing a derivative takes work, so a derivative address carries a token
 * signed for that style and original, and a request without a valid token is
 * only served a derivative already drawn.
 */
@Component
public class ImageStyleManager {

    /** Adding image styles and choosing their effects. */
    public static final String ADMINISTER_IMAGE_STYLES = "administer image styles";

    public static final String STYLES_DIRECTORY = "styles";

    public static final String TOKEN_PARAMETER = "itok";

    private static final String TOKEN_IDENTITY = "image_style";

    private final ConfigStore configStore;
    private final PluginRegistry registry;
    private final ImageToolkit toolkit;
    private final FileService files;
    private final FileSchemes.Registry schemes;
    private final ActionLinkTokenService tokens;

    public ImageStyleManager(ConfigStore configStore, PluginRegistry registry, ImageToolkit toolkit,
            FileService files, FileSchemes.Registry schemes, ActionLinkTokenService tokens) {
        this.configStore = configStore;
        this.registry = registry;
        this.toolkit = toolkit;
        this.files = files;
        this.schemes = schemes;
        this.tokens = tokens;
    }

    /** Adds the styles a site starts with, as the application starts, when it has none of them. */
    @EventListener(ApplicationReadyEvent.class)
    public void installDefaults() {
        installIfMissing(scaled("thumbnail", "Thumbnail (100×100)", 100, 100));
        installIfMissing(scaled("medium", "Medium (220×220)", 220, 220));
        installIfMissing(scaled("large", "Large (480×480)", 480, 480));
        installIfMissing(scaled("wide", "Wide (1090)", 1090, null));
        installIfMissing(new ImageStyle("square", "Square (300×300)", List.of(new EffectConfig("crop", ScaleAndCropEffect.ID,
                0, Map.of("width", 300, "height", 300)))));
    }

    private static ImageStyle scaled(String id, String label, int width, Integer height) {
        Map<String, Object> settings = (height == null)
                ? Map.of("width", width, ScaleEffect.UPSCALE, false)
                : Map.of("width", width, "height", height, ScaleEffect.UPSCALE, false);
        return new ImageStyle(id, label, List.of(new EffectConfig("scale", ScaleEffect.ID, 0, settings)));
    }

    private void installIfMissing(ImageStyle style) {
        if (find(style.id()).isEmpty()) {
            configStore.save(ImageStyle.configName(style.id()), style);
        }
    }

    public Optional<ImageStyle> find(String id) {
        return Optional.ofNullable(configStore.read(ImageStyle.configName(id), ImageStyle.class, null));
    }

    /** Every style, by label. */
    public List<ImageStyle> all() {
        List<ImageStyle> styles = new ArrayList<>();
        for (String name : configStore.listNames(ImageStyle.CONFIG_PREFIX)) {
            find(name.substring(ImageStyle.CONFIG_PREFIX.length() + 1)).ifPresent(styles::add);
        }
        return styles.stream().sorted(Comparator.comparing(ImageStyle::label)).toList();
    }

    /** Saves the style and removes what was drawn with it before, so it is drawn again the new way. */
    public void save(ImageStyle style) {
        configStore.save(ImageStyle.configName(style.id()), style);
        flush(style.id());
    }

    public void delete(String id) {
        configStore.delete(ImageStyle.configName(id));
        flush(id);
    }

    /** Removes every derivative drawn with the style, in every scheme. */
    public void flush(String styleId) {
        for (String scheme : schemes.schemes().keySet()) {
            try {
                schemes.find(scheme).orElseThrow().deleteTree(STYLES_DIRECTORY + "/" + styleId);
            } catch (IOException failure) {
                throw new UncheckedIOException(failure);
            }
        }
    }

    public PluginManager<ImageEffect> effects() {
        return registry.managerFor(ImageEffect.class);
    }

    /** Where a derivative of a file drawn with a style is kept, in the file's own scheme. */
    public static String derivativePath(String styleId, ManagedFile file) {
        return STYLES_DIRECTORY + "/" + styleId + "/" + file.scheme() + "/" + file.path();
    }

    /** The token a derivative address carries, signed for the style and the original. */
    public String token(String styleId, String sourceUri) {
        return tokens.token(TOKEN_IDENTITY + ":" + styleId + ":" + sourceUri, TOKEN_IDENTITY).substring(0, 8);
    }

    public boolean validToken(String styleId, String sourceUri, String token) {
        return token != null && MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                token(styleId, sourceUri).getBytes(StandardCharsets.UTF_8));
    }

    /** The address a derivative of a file drawn with a style is fetched at. */
    public String url(String styleId, ManagedFile file) {
        String base = file.scheme().equals(FileSchemes.PUBLIC) ? FileSchemes.PUBLIC_URL_PREFIX
                : FileService.PRIVATE_URL_PREFIX;
        return base + "/" + derivativePath(styleId, file) + "?" + TOKEN_PARAMETER + "=" + token(styleId, file.uri());
    }

    /** Whether a drawn derivative is there and no older than its original. */
    public boolean upToDate(ImageStyle style, ManagedFile file) throws IOException {
        var scheme = schemes.find(file.scheme()).orElseThrow();
        var drawn = scheme.modified(derivativePath(style.id(), file));
        var original = scheme.modified(file.path());
        return drawn.isPresent() && original.isPresent() && !drawn.get().isBefore(original.get());
    }

    /** Draws the derivative of a file with a style and keeps it. */
    public void draw(ImageStyle style, ManagedFile file) throws IOException {
        ToolkitImage image;
        try (InputStream original = files.read(file)) {
            image = toolkit.load(original);
        }
        PluginManager<ImageEffect> effects = effects();
        for (EffectConfig effect : style.effects()) {
            if (effects.has(effect.plugin())) {
                image = effects.get(effect.plugin()).apply(toolkit, image, effect.settings());
            }
        }
        ByteArrayOutputStream drawn = new ByteArrayOutputStream();
        toolkit.save(image, extension(file.path()), drawn);
        schemes.find(file.scheme()).orElseThrow()
                .write(derivativePath(style.id(), file), new ByteArrayInputStream(drawn.toByteArray()));
    }

    /** Reads a drawn derivative. */
    public InputStream read(ImageStyle style, ManagedFile file) throws IOException {
        return schemes.find(file.scheme()).orElseThrow().read(derivativePath(style.id(), file));
    }

    /**
     * The size an image of the given size comes out at with a style, or nothing
     * when an effect cannot tell in advance.
     */
    public Optional<ImageSize> transformedSize(ImageStyle style, ImageSize size) {
        PluginManager<ImageEffect> effects = effects();
        Optional<ImageSize> current = Optional.of(size);
        for (EffectConfig effect : style.effects()) {
            if (effects.has(effect.plugin())) {
                current = current.flatMap(known -> effects.get(effect.plugin())
                        .transformedSize(known, effect.settings()));
            }
        }
        return current;
    }

    /** Removes a deleted file's derivatives of every style. */
    @EventListener
    public void onFileDeleted(EntityEvent event) {
        if (event.phase() != EntityEvent.Phase.DELETE || !event.entityType().equals(FileEntityType.ID)) {
            return;
        }
        ManagedFile file = ManagedFile.of((EntityData) event.entity());
        var scheme = schemes.find(file.scheme()).orElseThrow();
        try {
            for (ImageStyle style : all()) {
                scheme.delete(derivativePath(style.id(), file));
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String extension(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }
}
