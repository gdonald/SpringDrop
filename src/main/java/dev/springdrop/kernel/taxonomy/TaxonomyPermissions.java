package dev.springdrop.kernel.taxonomy;

import dev.springdrop.kernel.permission.PermissionDefinition;
import dev.springdrop.kernel.permission.PermissionProvider;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** The permissions each vocabulary adds: creating, editing, and deleting its terms. */
@Component
public class TaxonomyPermissions implements PermissionProvider {

    public static final String PROVIDER = "taxonomy";

    /** Adding vocabularies and managing every vocabulary's terms. */
    public static final String ADMINISTER_TAXONOMY = "administer taxonomy";

    private final VocabularyManager vocabularies;

    public TaxonomyPermissions(VocabularyManager vocabularies) {
        this.vocabularies = vocabularies;
    }

    public static String create(String vocabulary) {
        return "create terms in " + vocabulary;
    }

    public static String edit(String vocabulary) {
        return "edit terms in " + vocabulary;
    }

    public static String delete(String vocabulary) {
        return "delete terms in " + vocabulary;
    }

    @Override
    public List<PermissionDefinition> permissions() {
        List<PermissionDefinition> permissions = new ArrayList<>();
        for (Vocabulary vocabulary : vocabularies.all()) {
            String label = vocabulary.label();
            permissions.add(PermissionDefinition.of(create(vocabulary.id()), label + ": Create terms", PROVIDER));
            permissions.add(PermissionDefinition.of(edit(vocabulary.id()), label + ": Edit terms", PROVIDER));
            permissions.add(PermissionDefinition.of(delete(vocabulary.id()), label + ": Delete terms", PROVIDER));
        }
        return List.copyOf(permissions);
    }
}
