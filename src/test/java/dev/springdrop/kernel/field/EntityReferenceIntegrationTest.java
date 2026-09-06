package dev.springdrop.kernel.field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.springdrop.kernel.entity.BundleDefinition;
import dev.springdrop.kernel.entity.BundleManager;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.entity.EntityValidationException;
import dev.springdrop.kernel.entity.EntityValueLookup;
import dev.springdrop.kernel.field.types.EntityReferenceFieldType;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootTest
class EntityReferenceIntegrationTest extends AbstractIntegrationTest {

    record Review(long id, String label) {
    }

    record ReviewType(String id, String label) {
    }

    record Album(long id, String label) {
    }

    static final EntityType REVIEW = EntityType.content("review", Review.class)
            .withBundles("type", "review_type");

    static final EntityType REVIEW_TYPE = EntityType.config("review_type", ReviewType.class);

    static final EntityType ALBUM = EntityType.content("album", Album.class);

    private static final String BUNDLE = "album_review";

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private BundleManager bundleManager;

    @Autowired
    private EntityReferenceResolver references;

    @Autowired
    private EntityValueLookup valueLookup;

    @BeforeEach
    void aReviewBundleReferencingAlbums() {
        entityTypeManager.installStorage("review");
        entityTypeManager.installStorage("album");
        bundleManager.save("review", new BundleDefinition(BUNDLE, "Album review"));

        fields.createStorage(new FieldStorageConfig("album", "review", EntityReferenceFieldType.ID, 1,
                Map.of(EntityReferenceFieldType.TARGET_TYPE, "album")));
        fields.createInstance(FieldInstanceConfig.of("album", "review", BUNDLE, "Album"));

        entities.save(new EntityData("album", 1L, null, null, "Kind of Blue",
                EntityData.DEFAULT_LANGCODE, null, Map.of()));
    }

    @AfterEach
    void removeFieldsAndBundle() {
        fields.fieldNames("review", BUNDLE).forEach(field -> fields.deleteStorage("review", field));
        bundleManager.delete("review", BUNDLE);
        entities.delete("album", 1L);
    }

    private void save(long id, Map<String, Object> values) {
        entities.save(EntityData.of("review", id, BUNDLE, "A review", values));
    }

    @Test
    void aReferenceToAnEntityThatExistsPersists() {
        save(1L, Map.of("album", 1L));

        assertThat(entities.load("review", 1L))
                .hasValueSatisfying(loaded -> assertThat(((Number) loaded.fields().get("album")).longValue())
                        .isEqualTo(1L));
    }

    @Test
    void aReferenceToAnEntityThatIsNotThereIsRejected() {
        assertThatThrownBy(() -> save(2L, Map.of("album", 404L)))
                .isInstanceOf(EntityValidationException.class);
    }

    @Test
    void aLoadedReferenceResolvesToItsTarget() {
        save(3L, Map.of("album", 1L));
        EntityData review = entities.load("review", 3L).orElseThrow();

        assertThat(references.target(review, "album"))
                .hasValueSatisfying(album -> assertThat(album.label()).isEqualTo("Kind of Blue"));
    }

    @Test
    void anEmptyReferenceResolvesToNothing() {
        save(4L, Map.of());
        EntityData review = entities.load("review", 4L).orElseThrow();

        assertThat(references.target(review, "album")).isEmpty();
        assertThat(references.targets(review, "album")).isEmpty();
    }

    @Test
    void aFieldThatIsNotAReferenceResolvesToNothing() {
        fields.createStorage(FieldStorageConfig.single("headline", "review", "string"));
        fields.createInstance(FieldInstanceConfig.of("headline", "review", BUNDLE, "Headline"));
        save(5L, Map.of("album", 1L, "headline", "A landmark record"));
        EntityData review = entities.load("review", 5L).orElseThrow();

        assertThat(references.targets(review, "headline")).isEmpty();
    }

    @Test
    void severalReferencesResolveInTheOrderTheFieldHoldsThem() {
        entities.save(new EntityData("album", 2L, null, null, "Blue Train",
                EntityData.DEFAULT_LANGCODE, null, Map.of()));
        fields.createStorage(new FieldStorageConfig("related", "review", EntityReferenceFieldType.ID,
                FieldStorageConfig.UNLIMITED, Map.of(EntityReferenceFieldType.TARGET_TYPE, "album")));
        fields.createInstance(FieldInstanceConfig.of("related", "review", BUNDLE, "Related albums"));

        save(6L, Map.of("related", List.of(2L, 1L)));
        EntityData review = entities.load("review", 6L).orElseThrow();

        assertThat(references.targets(review, "related")).extracting(entity -> entity.label())
                .containsExactly("Blue Train", "Kind of Blue");

        entities.delete("album", 2L);
    }

    @Test
    void theReferenceTypeDeclaresItsTargetSettingsAndDefaults() {
        FieldType type = fields.fieldType(EntityReferenceFieldType.ID);

        assertThat(type.id()).isEqualTo(EntityReferenceFieldType.ID);
        assertThat(type.properties()).isNotEmpty();
        assertThat(type.defaultWidget()).isEqualTo("entity_reference_autocomplete");
        assertThat(type.defaultFormatter()).isEqualTo("entity_reference_label");
        assertThat(type.defaultStorageSettings()).isEmpty();
        assertThat(type.defaultInstanceSettings()).containsKey(EntityReferenceFieldType.TARGET_BUNDLES);
    }

    @Test
    void storageIsNotAskedWhetherAValueIsTaken() {
        assertThat(valueLookup.valueInUse("album", 1L)).isFalse();
        assertThat(valueLookup.referenceExists("album", 1L)).isTrue();
        assertThat(valueLookup.referenceExists("nonesuch", 1L)).isFalse();
    }

    @Test
    void aReferenceFieldWithNoTargetTypeResolvesToNothing() {
        fields.createStorage(FieldStorageConfig.single("unset", "review", EntityReferenceFieldType.ID));
        fields.createInstance(FieldInstanceConfig.of("unset", "review", BUNDLE, "Unset"));
        save(7L, Map.of("album", 1L));
        EntityData review = entities.load("review", 7L).orElseThrow();

        assertThat(references.targets(review.withFields(Map.of("unset", 1L)), "unset")).isEmpty();
    }

    @TestConfiguration
    static class ReviewTypes {

        @Bean
        EntityTypeProvider reviewTypes() {
            return () -> List.of(REVIEW, REVIEW_TYPE, ALBUM);
        }
    }
}
