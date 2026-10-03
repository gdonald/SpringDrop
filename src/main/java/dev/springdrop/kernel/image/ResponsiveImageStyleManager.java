package dev.springdrop.kernel.image;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.ImageSize;
import dev.springdrop.kernel.file.ManagedFile;
import dev.springdrop.kernel.theme.Breakpoint;
import dev.springdrop.kernel.theme.BreakpointGroup;
import dev.springdrop.kernel.theme.BreakpointManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * The site's responsive image styles, and drawing an image with one.
 *
 * <p>An image is drawn as a {@code picture} element with one {@code source} per
 * breakpoint that has mappings, widest breakpoint first, since a browser takes
 * the first source whose media query matches. A breakpoint of single image
 * styles lists one address per pixel density; a breakpoint of sizes lists each
 * image style's address with the width it draws at, and the sizes. The
 * {@code img} inside draws the fallback image style.
 */
@Component
public class ResponsiveImageStyleManager {

    private final ConfigStore configStore;
    private final ImageStyleManager imageStyles;
    private final BreakpointManager breakpoints;
    private final FileService files;

    public ResponsiveImageStyleManager(ConfigStore configStore, ImageStyleManager imageStyles,
            BreakpointManager breakpoints, FileService files) {
        this.configStore = configStore;
        this.imageStyles = imageStyles;
        this.breakpoints = breakpoints;
        this.files = files;
    }

    public Optional<ResponsiveImageStyle> find(String id) {
        return Optional.ofNullable(configStore.read(ResponsiveImageStyle.configName(id),
                ResponsiveImageStyle.class, null));
    }

    /** Every responsive image style, by label. */
    public List<ResponsiveImageStyle> all() {
        List<ResponsiveImageStyle> styles = new ArrayList<>();
        for (String name : configStore.listNames(ResponsiveImageStyle.CONFIG_PREFIX)) {
            find(name.substring(ResponsiveImageStyle.CONFIG_PREFIX.length() + 1)).ifPresent(styles::add);
        }
        return styles.stream().sorted(Comparator.comparing(ResponsiveImageStyle::label)).toList();
    }

    public void save(ResponsiveImageStyle style) {
        configStore.save(ResponsiveImageStyle.configName(style.id()), style);
    }

    public void delete(String id) {
        configStore.delete(ResponsiveImageStyle.configName(id));
    }

    /**
     * The markup drawing an image with a responsive image style.
     *
     * @param size the original's size, or null when it is not known
     * @param attributes the {@code img} attributes beyond {@code src}, {@code width}, and {@code height},
     *     already written as {@code name="value"} pairs, each led by a space
     */
    public String render(ResponsiveImageStyle style, ManagedFile file, ImageSize size, String attributes) {
        StringBuilder picture = new StringBuilder("<picture>");
        Optional<BreakpointGroup> group = breakpoints.group(style.breakpointGroup());
        List<Breakpoint> widestFirst = group.map(BreakpointGroup::breakpoints).orElse(List.of()).reversed();
        for (Breakpoint breakpoint : widestFirst) {
            source(style, breakpoint, file, size).ifPresent(picture::append);
        }
        picture.append("<img src=\"").append(HtmlUtils.htmlEscape(address(style.fallbackImageStyle(), file)))
                .append("\"");
        drawnSize(style.fallbackImageStyle(), size).ifPresent(drawn -> picture.append(" width=\"")
                .append(drawn.width()).append("\" height=\"").append(drawn.height()).append("\""));
        return picture.append(attributes).append("></picture>").toString();
    }

    private Optional<String> source(ResponsiveImageStyle style, Breakpoint breakpoint, ManagedFile file,
            ImageSize size) {
        List<String> candidates = new ArrayList<>();
        String sizes = "";
        for (ResponsiveImageMapping mapping : style.mappingsFor(breakpoint.id())) {
            if (mapping.type().equals(ResponsiveImageMapping.SIZES)) {
                sizes = mapping.sizes();
                for (String imageStyle : mapping.sizesImageStyles()) {
                    drawnSize(imageStyle, size).ifPresent(drawn ->
                            candidates.add(address(imageStyle, file) + " " + drawn.width() + "w"));
                }
            } else if (usable(mapping.imageStyle())) {
                candidates.add(address(mapping.imageStyle(), file) + " " + mapping.multiplier());
            }
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder source = new StringBuilder("<source");
        if (!breakpoint.mediaQuery().equals(Breakpoint.ALL)) {
            source.append(" media=\"").append(HtmlUtils.htmlEscape(breakpoint.mediaQuery())).append("\"");
        }
        source.append(" srcset=\"").append(HtmlUtils.htmlEscape(String.join(", ", candidates))).append("\"");
        if (!sizes.isBlank()) {
            source.append(" sizes=\"").append(HtmlUtils.htmlEscape(sizes)).append("\"");
        }
        source.append(" type=\"").append(HtmlUtils.htmlEscape(file.mime())).append("\">");
        return Optional.of(source.toString());
    }

    private boolean usable(String imageStyle) {
        return imageStyle.equals(ResponsiveImageStyle.ORIGINAL) || imageStyles.find(imageStyle).isPresent();
    }

    /** Where the image is fetched drawn with an image style, or as it is for the original or a missing style. */
    private String address(String imageStyle, ManagedFile file) {
        return imageStyles.find(imageStyle).map(found -> imageStyles.url(found.id(), file))
                .orElseGet(() -> files.url(file));
    }

    private Optional<ImageSize> drawnSize(String imageStyle, ImageSize size) {
        if (size == null) {
            return Optional.empty();
        }
        return imageStyles.find(imageStyle).map(found -> imageStyles.transformedSize(found, size))
                .orElse(Optional.of(size));
    }
}
