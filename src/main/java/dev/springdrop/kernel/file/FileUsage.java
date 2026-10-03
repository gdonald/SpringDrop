package dev.springdrop.kernel.file;

/** One thing using a file: the module that records it, the kind of thing, its id, and how many times. */
public record FileUsage(long fileId, String module, String type, String id, int count) {
}
