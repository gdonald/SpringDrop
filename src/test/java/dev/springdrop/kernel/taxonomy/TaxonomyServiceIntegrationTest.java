package dev.springdrop.kernel.taxonomy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.permission.PermissionRegistry;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TaxonomyServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String PLACES = "places";

    private static final String TAGS = "tags";

    @Autowired
    private TaxonomyService taxonomy;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private VocabularyManager vocabularies;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private PermissionRegistry permissions;

    @BeforeEach
    void twoVocabularies() {
        clear();
        vocabularies.save(Vocabulary.of(PLACES, "Places"));
        vocabularies.save(Vocabulary.of(TAGS, "Tags").describedAs("Words that describe content."));
    }

    @AfterEach
    void nothingLeft() {
        clear();
    }

    private void clear() {
        queries.query(TaxonomyEntityType.ID).ids().forEach(id -> taxonomy.delete(((Number) id).longValue()));
        vocabularies.all().forEach(vocabulary -> vocabularies.delete(vocabulary.id()));
    }

    private EntityData term(String vocabulary, String name, long parent, int weight) {
        Map<String, Object> fields = new LinkedHashMap<>(taxonomy.create(vocabulary).fields());
        fields.put(TaxonomyEntityType.PARENT, parent);
        fields.put(TaxonomyEntityType.WEIGHT, weight);
        return taxonomy.save(EntityData.of(TaxonomyEntityType.ID, null, vocabulary, name, fields));
    }

    private static long idOf(EntityData term) {
        return ((Number) term.id()).longValue();
    }

    private EntityData moved(EntityData term, long parent) {
        Map<String, Object> fields = new LinkedHashMap<>(term.fields());
        fields.put(TaxonomyEntityType.PARENT, parent);
        return term.withFields(fields);
    }

    @Test
    void theTreeListsEachTermBeforeTheTermsUnderItSiblingsLightestFirstThenByName() {
        EntityData europe = term(PLACES, "Europe", TaxonomyEntityType.ROOT, 0);
        EntityData france = term(PLACES, "France", idOf(europe), 0);
        term(PLACES, "Paris", idOf(france), 0);
        term(PLACES, "Belgium", idOf(europe), 0);
        term(PLACES, "Asia", TaxonomyEntityType.ROOT, 0);
        term(PLACES, "Antarctica", TaxonomyEntityType.ROOT, 5);

        assertThat(taxonomy.tree(PLACES)).extracting(TermTreeItem::name, TermTreeItem::depth).containsExactly(
                tuple("Asia", 0),
                tuple("Europe", 0),
                tuple("Belgium", 1),
                tuple("France", 1),
                tuple("Paris", 2),
                tuple("Antarctica", 0));
    }

    @Test
    void aTermWhoseParentIsGoneIsListedAtTheTop() {
        EntityData europe = term(PLACES, "Europe", TaxonomyEntityType.ROOT, 0);
        EntityData france = term(PLACES, "France", idOf(europe), 0);

        entities.save(moved(france, 999_999L));

        assertThat(taxonomy.tree(PLACES)).extracting(TermTreeItem::name, TermTreeItem::depth)
                .contains(tuple("France", 0));
    }

    @Test
    void aParentHasToBeATermOfTheSameVocabularyThatIsNotTheTermOrBelowIt() {
        EntityData europe = term(PLACES, "Europe", TaxonomyEntityType.ROOT, 0);
        EntityData france = term(PLACES, "France", idOf(europe), 0);
        EntityData weather = term(TAGS, "Weather", TaxonomyEntityType.ROOT, 0);

        assertThatThrownBy(() -> term(PLACES, "Lost", 999_999L, 0)).isInstanceOf(TermHierarchyException.class);
        assertThatThrownBy(() -> taxonomy.save(moved(france, idOf(weather))))
                .isInstanceOf(TermHierarchyException.class);
        assertThatThrownBy(() -> taxonomy.save(moved(europe, idOf(europe))))
                .isInstanceOf(TermHierarchyException.class);
        assertThatThrownBy(() -> taxonomy.save(moved(europe, idOf(france))))
                .isInstanceOf(TermHierarchyException.class);
    }

    @Test
    void termsStoredInALoopStillGiveAnEndToWhatIsBelowThem() {
        EntityData europe = term(PLACES, "Europe", TaxonomyEntityType.ROOT, 0);
        EntityData france = term(PLACES, "France", idOf(europe), 0);

        entities.save(moved(europe, idOf(france)));

        assertThat(taxonomy.descendantsOf(idOf(europe))).containsExactlyInAnyOrder(idOf(france), idOf(europe));
    }

    @Test
    void deletingATermDeletesTheTermsBelowIt() {
        EntityData europe = term(PLACES, "Europe", TaxonomyEntityType.ROOT, 0);
        EntityData france = term(PLACES, "France", idOf(europe), 0);
        EntityData paris = term(PLACES, "Paris", idOf(france), 0);
        EntityData asia = term(PLACES, "Asia", TaxonomyEntityType.ROOT, 0);

        assertThat(taxonomy.descendantsOf(idOf(europe))).containsExactlyInAnyOrder(idOf(france), idOf(paris));
        taxonomy.delete(idOf(europe));

        assertThat(taxonomy.termsIn(PLACES)).extracting(EntityData::id).containsExactly(asia.id());
    }

    @Test
    void deletingAVocabularysTermsLeavesTheOthers() {
        term(PLACES, "Europe", TaxonomyEntityType.ROOT, 0);
        EntityData weather = term(TAGS, "Weather", TaxonomyEntityType.ROOT, 0);

        taxonomy.deleteTermsIn(PLACES);

        assertThat(taxonomy.termsIn(PLACES)).isEmpty();
        assertThat(taxonomy.termsIn(TAGS)).extracting(EntityData::id).containsExactly(weather.id());
    }

    @Test
    void aTermWithNoParentOrWeightStoredSitsAtTheTopAsLightAsAny() {
        EntityData bare = EntityData.of(TaxonomyEntityType.ID, 1L, PLACES, "Bare", Map.of());

        assertThat(TaxonomyService.parentOf(bare)).isEqualTo(TaxonomyEntityType.ROOT);
        assertThat(TaxonomyService.weightOf(bare)).isZero();
    }

    @Test
    void eachVocabularyOffersItsOwnPermissions() {
        assertThat(permissions.providedBy(TaxonomyPermissions.PROVIDER)).extracting(permission -> permission.name())
                .contains(TaxonomyPermissions.create(PLACES), TaxonomyPermissions.edit(TAGS),
                        TaxonomyPermissions.delete(TAGS));
    }

    @Test
    void vocabulariesAreListedLightestFirstThenByLabel() {
        vocabularies.save(new Vocabulary("topics", "Topics", "", -1));

        assertThat(vocabularies.all()).extracting(Vocabulary::label).containsExactly("Topics", "Places", "Tags");
    }
}
