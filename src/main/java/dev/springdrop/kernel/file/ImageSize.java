package dev.springdrop.kernel.file;

/** An image's width and height in pixels. */
public record ImageSize(int width, int height) {

    public boolean smallerThan(int minWidth, int minHeight) {
        return width < minWidth || height < minHeight;
    }

    public boolean largerThan(int maxWidth, int maxHeight) {
        return width > maxWidth || height > maxHeight;
    }
}
