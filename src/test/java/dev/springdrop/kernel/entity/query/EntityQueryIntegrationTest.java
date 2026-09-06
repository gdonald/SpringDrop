package dev.springdrop.kernel.entity.query;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.entity.BundleDefinition;
import dev.springdrop.kernel.entity.BundleManager;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

@SpringBootTest
class EntityQueryIntegrationTest extends AbstractIntegrationTest {

    record Product(long id, String label) {
    }

    record ProductType(String id, String label) {
    }

    static final EntityType PRODUCT = EntityType.content("product", Product.class)
            .withBundles("type", "product_type");

    static final EntityType PRODUCT_TYPE = EntityType.config("product_type", ProductType.class);

    /** A revisionable catalog, for querying one revision at a time. */
    static final EntityType EDITION = EntityType.content("edition", Product.class)
            .withBundles("type", "product_type")
            .withRevisions();

    private static final List<String> STOCK_FIELDS = List.of("category", "price");

    static final String HIDE_EXPENSIVE_TAG = "hide_expensive";

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private BundleManager bundleManager;

    @Autowired
    private FieldConfigManager fieldConfigManager;

    @BeforeEach
    void catalogOfFourProducts() {
        entityTypeManager.installStorage("product");
        bundleManager.save("product", new BundleDefinition("stock", "Stock item"));
        STOCK_FIELDS.forEach(field -> attachField("product", "stock", field));
        entities.delete("product", 1L);
        entities.delete("product", 2L);
        entities.delete("product", 3L);
        entities.delete("product", 4L);

        save(1L, "Anvil", "tools", 100);
        save(2L, "Balloon", "toys", 5);
        save(3L, "Chisel", "tools", 20);
        save(4L, "Drum", "music", 250);
    }

    private void attachField(String entityTypeId, String bundle, String field) {
        fieldConfigManager.createStorage(FieldStorageConfig.single(field, entityTypeId, "string"));
        fieldConfigManager.createInstance(FieldInstanceConfig.of(field, entityTypeId, bundle, field));
    }

    private void save(long id, String label, String category, int price) {
        entities.save(EntityData.of("product", id, "stock", label,
                Map.of("category", category, "price", price)));
    }

    private EntityQuery query() {
        return queries.query("product").inLanguage(EntityData.DEFAULT_LANGCODE);
    }

    @Test
    void filtersByAFieldValue() {
        List<Object> ids = query().condition(Condition.equal("category", "tools")).ids();

        assertThat(ids).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void filtersByABaseKey() {
        assertThat(query().condition(Condition.equal("label", "Balloon")).ids()).containsExactly(2L);
    }

    @Test
    void filtersByAnOrGroupOfFieldConditions() {
        List<Object> ids = query()
                .condition(Condition.anyOf(
                        Condition.equal("category", "toys"),
                        Condition.equal("category", "music")))
                .sort(Sort.ascending("label"))
                .ids();

        assertThat(ids).containsExactly(2L, 4L);
    }

    @Test
    void combinesAnOrGroupWithAnotherCondition() {
        List<Object> ids = query()
                .condition(Condition.allOf(
                        Condition.equal("category", "tools"),
                        Condition.anyOf(
                                Condition.equal("price", 20),
                                Condition.equal("price", 999))))
                .ids();

        assertThat(ids).containsExactly(3L);
    }

    @Test
    void sortsDescendingByAFieldValue() {
        assertThat(query().sort(Sort.descending("price")).ids()).containsExactly(4L, 1L, 3L, 2L);
    }

    @Test
    void paginatesWithARange() {
        List<Object> ids = query().sort(Sort.ascending("label")).range(1, 2).ids();

        assertThat(ids).containsExactly(2L, 3L);
    }

    @Test
    void countsMatchingEntities() {
        assertThat(query().condition(Condition.equal("category", "tools")).count()).isEqualTo(2);
    }

    @Test
    void comparesFieldValuesWithGreaterAndLessThan() {
        assertThat(query().condition(Condition.greaterThan("price", 100)).ids()).containsExactly(4L);
        assertThat(query().condition(Condition.lessThan("price", 20)).ids()).containsExactly(2L);
    }

    @Test
    void excludesAValueWithNotEqual() {
        assertThat(query().condition(Condition.notEqual("category", "tools")).ids())
                .containsExactlyInAnyOrder(2L, 4L);
    }

    @Test
    void matchesAnyOfSeveralValues() {
        assertThat(query().condition(Condition.in("category", List.of("toys", "music"))).ids())
                .containsExactlyInAnyOrder(2L, 4L);
        assertThat(query().condition(Condition.in("id", List.of(1L, 2L))).ids())
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void matchesASubstringOfAFieldValueAndOfABaseKey() {
        assertThat(query().condition(Condition.contains("category", "toy")).ids()).containsExactly(2L);
        assertThat(query().condition(Condition.contains("label", "hise")).ids()).containsExactly(3L);
    }

    @Test
    void readsOnlyTheRequestedLanguage() {
        entities.save(EntityData.of("product", 5L, "stock", "Einrad", Map.of("category", "sport", "price", 400))
                .withLangcode("de"));

        assertThat(queries.query("product").inLanguage("de")
                .condition(Condition.equal("category", "sport")).ids()).containsExactly(5L);
        assertThat(query().condition(Condition.equal("category", "sport")).ids()).isEmpty();

        entities.delete("product", 5L);
    }

    @Test
    void aQueryWithoutALanguageFilterSeesEveryTranslation() {
        entities.save(EntityData.of("product", 6L, "stock", "Einrad", Map.of("category", "sport", "price", 400))
                .withLangcode("de"));

        assertThat(queries.query("product").condition(Condition.equal("category", "sport")).ids())
                .containsExactly(6L);

        entities.delete("product", 6L);
    }

    @Test
    void filtersByOneRevisionOfAnEntity() {
        entityTypeManager.installStorage("edition");
        bundleManager.save("edition", new BundleDefinition("stock", "Stock item"));
        attachField("edition", "stock", "category");
        EntityData first = entities.save(EntityData.of("edition", 1L, "stock", "First", Map.of("category", "old")));
        EntityData second = entities.save(EntityData.of("edition", 1L, "stock", "Second", Map.of("category", "new")));

        assertThat(queries.query("edition").inLanguage(EntityData.DEFAULT_LANGCODE)
                .inRevision(first.revisionId())
                .condition(Condition.equal("category", "old")).ids()).containsExactly(1L);
        assertThat(queries.query("edition").inLanguage(EntityData.DEFAULT_LANGCODE)
                .inRevision(second.revisionId())
                .condition(Condition.equal("category", "old")).ids()).isEmpty();

        entities.delete("edition", 1L);
    }

    @Test
    void anAccessTagLetsAListenerNarrowTheResults() {
        List<Object> unrestricted = query().sort(Sort.ascending("label")).ids();
        List<Object> restricted = query()
                .accessTag(HIDE_EXPENSIVE_TAG)
                .sort(Sort.ascending("label"))
                .ids();

        assertThat(unrestricted).containsExactly(1L, 2L, 3L, 4L);
        assertThat(restricted).containsExactly(2L, 3L);
    }

    @Test
    void anUntaggedQueryIsLeftAlone() {
        assertThat(query().accessTags()).isEmpty();
        assertThat(query().ids()).hasSize(4);
    }

    @TestConfiguration
    static class ProductTypes {

        @Bean
        EntityTypeProvider productTypes() {
            return () -> List.of(PRODUCT, PRODUCT_TYPE, EDITION);
        }

        @Bean
        ExpensiveProductFilter expensiveProductFilter() {
            return new ExpensiveProductFilter();
        }
    }

    /** Stands in for the access system: hides what the viewer may not see. */
    static class ExpensiveProductFilter {

        @EventListener
        void narrow(EntityQueryAlterEvent event) {
            if (event.subject().accessTags().contains(HIDE_EXPENSIVE_TAG)) {
                event.subject().condition(Condition.lessThan("price", 100));
            }
        }
    }
}
