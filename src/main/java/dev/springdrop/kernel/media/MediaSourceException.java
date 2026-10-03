package dev.springdrop.kernel.media;

/** A source value a media source cannot use, with the reason shown to the person saving it. */
public class MediaSourceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MediaSourceException(String reason) {
        super(reason);
    }
}
