package dev.springdrop.kernel.image;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * What image effects are built from: loading an image, the operations on it,
 * and saving it. Core's toolkit is {@link Java2dToolkit}. A module replaces it
 * by registering another bean of this type marked primary.
 */
public interface ImageToolkit {

    /** Loads an image, failing when the content is not an image the toolkit reads. */
    ToolkitImage load(InputStream content) throws IOException;

    /** Saves the image in a format named by its extension, such as png or jpg. */
    void save(ToolkitImage image, String format, OutputStream target) throws IOException;

    ToolkitImage resize(ToolkitImage image, int width, int height);

    ToolkitImage crop(ToolkitImage image, int x, int y, int width, int height);

    ToolkitImage desaturate(ToolkitImage image);

    /**
     * Turns the image clockwise by a number of degrees, filling the corners a turn
     * that is not a right angle uncovers with a color written {@code #rrggbb}, or
     * leaving them transparent when the color is blank.
     */
    ToolkitImage rotate(ToolkitImage image, int degrees, String background);
}
