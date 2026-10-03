package dev.springdrop.kernel.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * The image toolkit Java ships: Java 2D for the operations and ImageIO for
 * reading and writing PNG, GIF, and JPEG. A JPEG has no transparency, so an
 * image saved as one is drawn over white first.
 */
@Component
public class Java2dToolkit implements ImageToolkit {

    record Java2dImage(BufferedImage image) implements ToolkitImage {

        @Override
        public int width() {
            return image.getWidth();
        }

        @Override
        public int height() {
            return image.getHeight();
        }
    }

    @Override
    public ToolkitImage load(InputStream content) throws IOException {
        BufferedImage image = ImageIO.read(content);
        if (image == null) {
            throw new IOException("The content is not an image the toolkit reads");
        }
        return new Java2dImage(image);
    }

    @Override
    public void save(ToolkitImage image, String format, OutputStream target) throws IOException {
        String writerFormat = format.toLowerCase(Locale.ROOT).equals("jpg") ? "jpeg" : format.toLowerCase(Locale.ROOT);
        BufferedImage source = buffered(image);
        BufferedImage output = writerFormat.equals("jpeg") ? opaque(source) : source;
        if (!ImageIO.write(output, writerFormat, target)) {
            throw new IOException("The toolkit cannot write " + format + " images");
        }
    }

    @Override
    public ToolkitImage resize(ToolkitImage image, int width, int height) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(buffered(image), 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return new Java2dImage(resized);
    }

    @Override
    public ToolkitImage crop(ToolkitImage image, int x, int y, int width, int height) {
        BufferedImage cropped = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = cropped.createGraphics();
        try {
            graphics.drawImage(buffered(image), -x, -y, null);
        } finally {
            graphics.dispose();
        }
        return new Java2dImage(cropped);
    }

    @Override
    public ToolkitImage desaturate(ToolkitImage image) {
        BufferedImage source = buffered(image);
        BufferedImage gray = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int argb = source.getRGB(x, y);
                int luminance = (int) Math.round(0.299 * ((argb >> 16) & 0xff) + 0.587 * ((argb >> 8) & 0xff)
                        + 0.114 * (argb & 0xff));
                gray.setRGB(x, y, (argb & 0xff000000) | (luminance << 16) | (luminance << 8) | luminance);
            }
        }
        return new Java2dImage(gray);
    }

    @Override
    public ToolkitImage rotate(ToolkitImage image, int degrees, String background) {
        double radians = Math.toRadians(degrees);
        double sin = Math.abs(Math.sin(radians));
        double cos = Math.abs(Math.cos(radians));
        int width = (int) Math.round(image.width() * cos + image.height() * sin);
        int height = (int) Math.round(image.width() * sin + image.height() * cos);
        BufferedImage rotated = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = rotated.createGraphics();
        try {
            if (!background.isBlank()) {
                graphics.setColor(Color.decode(background));
                graphics.fillRect(0, 0, width, height);
            }
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            AffineTransform turn = new AffineTransform();
            turn.translate(width / 2.0, height / 2.0);
            turn.rotate(radians);
            turn.translate(-image.width() / 2.0, -image.height() / 2.0);
            graphics.drawImage(buffered(image), turn, null);
        } finally {
            graphics.dispose();
        }
        return new Java2dImage(rotated);
    }

    private static BufferedImage buffered(ToolkitImage image) {
        return ((Java2dImage) image).image();
    }

    private static BufferedImage opaque(BufferedImage source) {
        BufferedImage flattened = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = flattened.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, Color.WHITE, null);
        } finally {
            graphics.dispose();
        }
        return flattened;
    }
}
