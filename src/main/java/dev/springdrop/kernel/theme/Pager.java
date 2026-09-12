package dev.springdrop.kernel.theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;

/**
 * The page links under a listing. Pages are numbered from one, and the previous
 * and next links are absent at the ends of the range so the theme can disable
 * them rather than draw a dead link.
 */
public record Pager(List<PageLink> pages, Optional<String> previousUrl, Optional<String> nextUrl) {

    /** One numbered page, and whether it is the one being read. */
    public record PageLink(int number, String url, boolean current) {
    }

    /** No pager: a listing that fits on one page draws nothing. */
    public static final Pager NONE = new Pager(List.of(), Optional.empty(), Optional.empty());

    public Pager {
        pages = List.copyOf(pages);
    }

    /** A pager over {@code totalPages} pages, with {@code url} building each page's address. */
    public static Pager of(int currentPage, int totalPages, IntFunction<String> url) {
        List<PageLink> pages = new ArrayList<>();
        for (int number = 1; number <= totalPages; number++) {
            pages.add(new PageLink(number, url.apply(number), number == currentPage));
        }
        return new Pager(
                pages,
                currentPage > 1 ? Optional.of(url.apply(currentPage - 1)) : Optional.empty(),
                currentPage < totalPages ? Optional.of(url.apply(currentPage + 1)) : Optional.empty());
    }
}
