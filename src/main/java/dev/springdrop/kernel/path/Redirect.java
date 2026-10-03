package dev.springdrop.kernel.path;

/** A path that sends the browser elsewhere: where to, and with which redirect status. */
public record Redirect(long id, String source, String destination, int status) {
}
