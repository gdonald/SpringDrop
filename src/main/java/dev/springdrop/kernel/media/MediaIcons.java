package dev.springdrop.kernel.media;

import dev.springdrop.kernel.file.FileSchemes;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.FileUsageService;
import dev.springdrop.kernel.file.ManagedFile;
import dev.springdrop.kernel.state.StateService;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * The thumbnails of media without an image of their own, such as documents: a
 * gray square naming the kind. Each is drawn and stored as a public managed
 * file the first time it is asked for, kept permanent by a use of its own, and
 * the same file is used for every item of that kind after that. The site
 * remembers an icon by its uri, which a later file cannot share.
 */
@Component
public class MediaIcons {

    public static final String DOCUMENT = "document";

    public static final String AUDIO = "audio";

    public static final String VIDEO = "video";

    static final String STATE_COLLECTION = "media.icons";

    static final String USAGE_TYPE = "media_icon";

    private static final int SIDE = 180;

    private final FileService files;
    private final FileUsageService usage;
    private final StateService state;

    public MediaIcons(FileService files, FileUsageService usage, StateService state) {
        this.files = files;
        this.usage = usage;
        this.state = state;
    }

    /** The managed file of a kind's icon, drawing and storing it when the site has none yet. */
    public long icon(String kind) throws IOException {
        Optional<ManagedFile> existing = state.get(STATE_COLLECTION, kind, String.class).flatMap(files::findByUri);
        return existing.isPresent() ? existing.get().id() : stored(kind);
    }

    private long stored(String kind) throws IOException {
        ManagedFile file = files.store(drawn(kind), kind + ".png", FileSchemes.PUBLIC, 0L);
        usage.add(file.id(), MediaEntityType.ID, USAGE_TYPE, kind);
        state.set(STATE_COLLECTION, kind, file.uri());
        return file.id();
    }

    private static byte[] drawn(String kind) throws IOException {
        BufferedImage image = new BufferedImage(SIDE, SIDE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(new Color(0xe9ecef));
            graphics.fillRect(0, 0, SIDE, SIDE);
            graphics.setColor(new Color(0x495057));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
            String label = kind.toUpperCase(Locale.ROOT);
            int width = graphics.getFontMetrics().stringWidth(label);
            graphics.drawString(label, (SIDE - width) / 2, SIDE / 2 + 8);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }
}
