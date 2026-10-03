package dev.springdrop.kernel.taxonomy;

/** One term in its vocabulary's tree, with how many levels down it sits. */
public record TermTreeItem(long id, String name, long parent, int weight, int depth) {
}
