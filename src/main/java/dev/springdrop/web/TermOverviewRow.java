package dev.springdrop.web;

import dev.springdrop.kernel.form.SelectOption;
import java.util.List;

/**
 * One term on its vocabulary's overview: where it sits in the tree, the
 * parents it may be moved under, and its weight among its siblings.
 */
public record TermOverviewRow(long id, String name, int depth, long parent, int weight, List<SelectOption> parents) {
}
