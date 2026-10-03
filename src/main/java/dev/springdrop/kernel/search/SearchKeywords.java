package dev.springdrop.kernel.search;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** The words of a search's keywords: runs of letters and digits, lower case, each once, at most 32. */
public final class SearchKeywords {

    public static final int MAX_WORDS = 32;

    private SearchKeywords() {
    }

    public static List<String> words(String keywords) {
        return Arrays.stream(keywords.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(word -> !word.isEmpty())
                .distinct()
                .limit(MAX_WORDS)
                .toList();
    }
}
