package dev.springdrop.kernel.log;

/** How much an error page tells its reader about the error behind it. */
public enum ErrorDisplay {

    /** Only that something went wrong. */
    NONE,

    /** The error's message as well. */
    ERRORS,

    /** The error's message and where in the code it happened. */
    ALL
}
