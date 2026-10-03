package dev.springdrop.kernel.image;

import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.file.FileItem;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.ImageSize;
import dev.springdrop.kernel.file.ManagedFile;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.path.PathAliasManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * What the image formatters share: the link an image can carry, how it loads,
 * and the attributes an image value gives its {@code img}.
 */
@Component
public class ImageMarkup {

    /** Where the image links to: nowhere, the entity it belongs to, or the original file. */
    public static final String IMAGE_LINK = "image_link";

    public static final String LINK_CONTENT = "content";

    public static final String LINK_FILE = "file";

    /** Whether the browser fetches the image at once or once it is scrolled near. */
    public static final String IMAGE_LOADING = "image_loading";

    public static final String LAZY = "lazy";

    public static final String EAGER = "eager";

    private final FileService files;
    private final EntityTypeManager entityTypeManager;
    private final PathAliasManager aliases;

    public ImageMarkup(FileService files, EntityTypeManager entityTypeManager, PathAliasManager aliases) {
        this.files = files;
        this.entityTypeManager = entityTypeManager;
        this.aliases = aliases;
    }

    /** The original's size as the value records it, or nothing when it does not. */
    public static Optional<ImageSize> storedSize(Object value) {
        String width = FileItem.part(value, FileItem.WIDTH);
        String height = FileItem.part(value, FileItem.HEIGHT);
        return (width.matches("\\d{1,5}") && height.matches("\\d{1,5}"))
                ? Optional.of(new ImageSize(Integer.parseInt(width), Integer.parseInt(height)))
                : Optional.empty();
    }

    /** The alternative text, title, and loading attributes, each led by a space. */
    public static String attributes(Object value, Map<String, Object> settings) {
        StringBuilder attributes = new StringBuilder(" alt=\"")
                .append(HtmlUtils.htmlEscape(FileItem.part(value, FileItem.ALT))).append("\"");
        String title = FileItem.part(value, FileItem.TITLE);
        if (!title.isBlank()) {
            attributes.append(" title=\"").append(HtmlUtils.htmlEscape(title)).append("\"");
        }
        return attributes.append(" loading=\"").append(loading(settings)).append("\"").toString();
    }

    static String loading(Map<String, Object> settings) {
        return EAGER.equals(settings.get(IMAGE_LOADING)) ? EAGER : LAZY;
    }

    static String link(Map<String, Object> settings) {
        Object link = settings.get(IMAGE_LINK);
        return (LINK_CONTENT.equals(link) || LINK_FILE.equals(link)) ? link.toString() : "";
    }

    /** The image wrapped in the link the settings ask for, when there is somewhere to link to. */
    public String linked(String image, FormatterContext context, ManagedFile file) {
        Optional<String> href = switch (link(context.settings())) {
            case LINK_FILE -> Optional.of(files.url(file));
            case LINK_CONTENT -> context.entity().flatMap(entity -> entityTypeManager.find(entity.entityType())
                    .map(type -> aliases.outbound(type.canonicalPath(entity.id()))));
            default -> Optional.empty();
        };
        return href.map(path -> "<a href=\"" + HtmlUtils.htmlEscape(path) + "\">" + image + "</a>").orElse(image);
    }

    /** The link and loading choices every image formatter offers. */
    public static List<FormElement> linkAndLoadingForm(String prefix, Map<String, Object> settings) {
        return List.of(
                FormElement.of(ElementType.SELECT, prefix + IMAGE_LINK).label("Link image to")
                        .value(link(settings))
                        .options(List.of(new SelectOption("", "Nothing"), new SelectOption(LINK_CONTENT, "Content"),
                                new SelectOption(LINK_FILE, "File"))),
                FormElement.of(ElementType.SELECT, prefix + IMAGE_LOADING).label("Loading")
                        .value(loading(settings))
                        .options(List.of(new SelectOption(LAZY, "Lazy, once scrolled near"),
                                new SelectOption(EAGER, "Eager, with the page"))));
    }

    /** Puts the link and loading choices a submission gives into the settings. */
    public static void putLinkAndLoading(String prefix, Map<String, String> submitted, Map<String, Object> settings) {
        settings.put(IMAGE_LINK, link(Map.of(IMAGE_LINK, submitted.getOrDefault(prefix + IMAGE_LINK, ""))));
        settings.put(IMAGE_LOADING, loading(Map.of(IMAGE_LOADING, submitted.getOrDefault(prefix + IMAGE_LOADING, ""))));
    }
}
