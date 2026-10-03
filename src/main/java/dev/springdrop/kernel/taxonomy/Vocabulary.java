package dev.springdrop.kernel.taxonomy;

/**
 * A vocabulary: a set of terms content is classified by, such as tags or
 * categories. Stored as the config entity {@code taxonomy_vocabulary.<id>},
 * which is also the bundle the Field API hangs its terms' fields on.
 */
public record Vocabulary(String id, String label, String description, int weight) {

    public static Vocabulary of(String id, String label) {
        return new Vocabulary(id, label, "", 0);
    }

    public Vocabulary describedAs(String newDescription) {
        return new Vocabulary(id, label, newDescription, weight);
    }
}
