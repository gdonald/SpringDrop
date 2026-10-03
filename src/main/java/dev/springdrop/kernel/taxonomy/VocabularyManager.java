package dev.springdrop.kernel.taxonomy;

import dev.springdrop.kernel.config.ConfigStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** The vocabularies the site has, each stored as the config entity {@code taxonomy_vocabulary.<id>}. */
@Component
public class VocabularyManager {

    private final ConfigStore configStore;

    public VocabularyManager(ConfigStore configStore) {
        this.configStore = configStore;
    }

    public static String configName(String id) {
        return TaxonomyEntityType.VOCABULARY_ID + "." + id;
    }

    public void save(Vocabulary vocabulary) {
        configStore.save(configName(vocabulary.id()), vocabulary);
    }

    public Optional<Vocabulary> find(String id) {
        return Optional.ofNullable(configStore.read(configName(id), Vocabulary.class, null));
    }

    public void delete(String id) {
        configStore.delete(configName(id));
    }

    /** Every vocabulary, lightest first and then by label. */
    public List<Vocabulary> all() {
        List<Vocabulary> vocabularies = new ArrayList<>();
        for (String name : configStore.listNames(TaxonomyEntityType.VOCABULARY_ID)) {
            find(name.substring(TaxonomyEntityType.VOCABULARY_ID.length() + 1)).ifPresent(vocabularies::add);
        }
        return vocabularies.stream()
                .sorted(Comparator.comparingInt(Vocabulary::weight).thenComparing(Vocabulary::label))
                .toList();
    }
}
