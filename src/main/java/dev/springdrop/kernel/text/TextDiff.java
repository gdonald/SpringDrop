package dev.springdrop.kernel.text;

import java.util.ArrayList;
import java.util.List;

/**
 * The word-by-word difference between two texts: the words both keep, the
 * words only the first has, and the words only the second has, in reading
 * order, with removed words ahead of the words added in their place. Words are
 * runs of characters between whitespace, and the longest run of words the two
 * texts share in order is what is kept.
 */
public final class TextDiff {

    /** Whether a part of the text is in both, only the old one, or only the new one. */
    public enum Change {
        KEPT,
        REMOVED,
        ADDED
    }

    /** A run of words and what happened to it. */
    public record Part(Change change, String text) {
    }

    private TextDiff() {
    }

    public static List<Part> words(String before, String after) {
        List<String> old = tokens(before);
        List<String> changed = tokens(after);
        int[][] shared = new int[old.size() + 1][changed.size() + 1];
        for (int left = old.size() - 1; left >= 0; left--) {
            for (int right = changed.size() - 1; right >= 0; right--) {
                shared[left][right] = old.get(left).equals(changed.get(right))
                        ? shared[left + 1][right + 1] + 1
                        : Math.max(shared[left + 1][right], shared[left][right + 1]);
            }
        }

        List<Part> parts = new ArrayList<>();
        int left = 0;
        int right = 0;
        while (left < old.size() || right < changed.size()) {
            if (left < old.size() && right < changed.size() && old.get(left).equals(changed.get(right))) {
                append(parts, Change.KEPT, old.get(left));
                left++;
                right++;
            } else if (left < old.size()
                    && (right == changed.size() || shared[left + 1][right] >= shared[left][right + 1])) {
                append(parts, Change.REMOVED, old.get(left));
                left++;
            } else {
                append(parts, Change.ADDED, changed.get(right));
                right++;
            }
        }
        return List.copyOf(parts);
    }

    private static List<String> tokens(String text) {
        return text.isBlank() ? List.of() : List.of(text.trim().split("\\s+"));
    }

    /** Adds a word to the run before it when that run changed the same way. */
    private static void append(List<Part> parts, Change change, String word) {
        if (!parts.isEmpty() && parts.getLast().change() == change) {
            parts.set(parts.size() - 1, new Part(change, parts.getLast().text() + " " + word));
        } else {
            parts.add(new Part(change, word));
        }
    }
}
