package dev.springdrop.kernel.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.springdrop.kernel.event.EntityEvent;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.schema.SchemaManager;
import dev.springdrop.kernel.validation.ConstraintSpec;
import dev.springdrop.kernel.validation.constraints.LengthConstraint;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

@SpringBootTest
class EntityCrudIntegrationTest extends AbstractIntegrationTest {

    record Article(long id, String label) {
    }

    record ArticleType(String id, String label) {
    }

    static final EntityType ARTICLE = EntityType.content("article", Article.class)
            .withBundles("type", "article_type")
            .withRevisions()
            .withTranslations();

    static final EntityType ARTICLE_TYPE = EntityType.config("article_type", ArticleType.class);

    static final EntityType MEMO = EntityType.content("memo", Article.class);

    /** Bundled and fieldable, but with one language and no revisions. */
    static final EntityType NOTE = EntityType.content("note", Article.class)
            .withBundles("type", "note_type");

    static final EntityType NOTE_TYPE = EntityType.config("note_type", ArticleType.class);

    /** Revisionable in one language. */
    static final EntityType LOG = EntityType.content("log", Article.class).withRevisions();

    private static final List<String> STORY_FIELDS = List.of("body", "tags");

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private BundleManager bundleManager;

    @Autowired
    private FieldTableStorage fieldTableStorage;

    @Autowired
    private FieldConfigManager fieldConfigManager;

    @Autowired
    private SchemaManager schemaManager;

    @Autowired
    private RecordingListener listener;

    @BeforeEach
    void installedTypeWithOneBundle() {
        entityTypeManager.installStorage("article");
        entityTypeManager.installStorage("memo");
        entityTypeManager.installStorage("note");
        entityTypeManager.installStorage("log");
        bundleManager.save("article", new BundleDefinition("story", "Story"));
        bundleManager.save("note", new BundleDefinition("reminder", "Reminder"));
        STORY_FIELDS.forEach(field -> attachField("article", "story", field));
        attachField("note", "reminder", "body");
        listener.clear();
    }

    private void attachField(String entityTypeId, String bundle, String field) {
        fieldConfigManager.createStorage(FieldStorageConfig.multiple(
                field, entityTypeId, "string", FieldStorageConfig.UNLIMITED));
        fieldConfigManager.createInstance(FieldInstanceConfig.of(field, entityTypeId, bundle, field));
    }

    private EntityData story(long id, String label, Map<String, Object> fields) {
        return EntityData.of("article", id, "story", label, fields);
    }

    @Test
    void savingANewEntityWritesItsBaseRowAndItsFieldTables() {
        entities.save(story(1L, "First", Map.of("body", "Hello", "tags", List.of("news", "local"))));

        assertThat(entities.load("article", 1L)).hasValueSatisfying(loaded -> {
            assertThat(loaded.label()).isEqualTo("First");
            assertThat(loaded.bundle()).isEqualTo("story");
            assertThat(loaded.fields()).containsEntry("body", "Hello");
            assertThat(loaded.fields()).containsEntry("tags", List.of("news", "local"));
        });
    }

    @Test
    void savingANewEntityAssignsItAUuidAndAFirstRevision() {
        EntityData saved = entities.save(story(2L, "Second", Map.of("body", "Text")));

        assertThat(saved.uuid()).isNotNull();
        assertThat(saved.revisionId()).isNotNull();
    }

    @Test
    void savingAnExistingEntityUpdatesItAndStartsANewRevision() {
        EntityData first = entities.save(story(3L, "Before", Map.of("body", "Old")));

        EntityData second = entities.save(story(3L, "After", Map.of("body", "New")));

        assertThat(second.revisionId()).isGreaterThan(first.revisionId());
        assertThat(entities.load("article", 3L)).hasValueSatisfying(loaded -> {
            assertThat(loaded.label()).isEqualTo("After");
            assertThat(loaded.fields()).containsEntry("body", "New");
        });
    }

    @Test
    void anEarlierRevisionStillHoldsItsOwnFieldValues() {
        EntityData first = entities.save(story(4L, "Before", Map.of("body", "Old")));
        entities.save(story(4L, "After", Map.of("body", "New")));

        assertThat(entities.load("article", 4L, EntityData.DEFAULT_LANGCODE, first.revisionId()))
                .hasValueSatisfying(loaded -> assertThat(loaded.fields()).containsEntry("body", "Old"));
    }

    @Test
    void eachLanguageHoldsItsOwnFieldValues() {
        entities.save(story(5L, "English", Map.of("body", "Hello")));
        EntityData saved = entities.save(story(5L, "Deutsch", Map.of("body", "Hallo")).withLangcode("de"));

        assertThat(entities.load("article", 5L, "de", saved.revisionId()))
                .hasValueSatisfying(loaded -> assertThat(loaded.fields()).containsEntry("body", "Hallo"));
    }

    @Test
    void savingANewEntityFiresPresaveThenInsert() {
        entities.save(story(6L, "Sixth", Map.of("body", "Text")));

        assertThat(listener.phases()).containsExactly(EntityEvent.Phase.PRESAVE, EntityEvent.Phase.INSERT);
    }

    @Test
    void savingAnExistingEntityFiresPresaveThenUpdate() {
        entities.save(story(7L, "Before", Map.of("body", "Old")));
        listener.clear();

        entities.save(story(7L, "After", Map.of("body", "New")));

        assertThat(listener.phases()).containsExactly(EntityEvent.Phase.PRESAVE, EntityEvent.Phase.UPDATE);
    }

    @Test
    void loadingFiresTheLoadEvent() {
        entities.save(story(8L, "Eighth", Map.of("body", "Text")));
        listener.clear();

        entities.load("article", 8L);

        assertThat(listener.phases()).containsExactly(EntityEvent.Phase.LOAD);
    }

    @Test
    void deletingFiresDeleteAndRemovesTheEntityWithItsFieldValues() {
        entities.save(story(9L, "Doomed", Map.of("body", "Text")));
        listener.clear();

        entities.delete("article", 9L);

        assertThat(listener.phases()).containsExactly(EntityEvent.Phase.DELETE);
        assertThat(entities.load("article", 9L)).isEmpty();
        assertThat(fieldTableStorage.read(ARTICLE, 9L, EntityData.DEFAULT_LANGCODE, STORY_FIELDS)).isEmpty();
    }

    @Test
    void deletingAnEntityThatIsNotThereDoesNothing() {
        entities.delete("article", 404L);

        assertThat(listener.phases()).isEmpty();
    }

    @Test
    void aConstraintViolationAbortsTheSaveBeforeAnythingIsWritten() {
        EntityData tooLong = story(10L, "A label that is far too long to store", Map.of("body", "Text"));

        assertThatThrownBy(() -> entities.save(tooLong))
                .isInstanceOf(EntityValidationException.class)
                .satisfies(thrown -> assertThat(((EntityValidationException) thrown).violations())
                        .extracting(violation -> violation.propertyPath())
                        .containsExactly("label"));
        assertThat(entities.load("article", 10L)).isEmpty();
    }

    @Test
    void loadingAnEntityThatIsNotThereFindsNothing() {
        assertThat(entities.load("article", 999L)).isEmpty();
    }

    @Test
    void anUnbundledTypeSavesAndLoadsWithoutFields() {
        EntityData memo = new EntityData("memo", 1L, null, null, "A memo", EntityData.DEFAULT_LANGCODE, null,
                Map.of("ignored", "value"));

        entities.save(memo);

        assertThat(entities.load("memo", 1L)).hasValueSatisfying(loaded -> {
            assertThat(loaded.label()).isEqualTo("A memo");
            assertThat(loaded.bundle()).isNull();
            assertThat(loaded.fields()).isEmpty();
        });
    }

    @Test
    void aFieldTheEntityDoesNotSetKeepsNoValues() {
        entities.save(story(11L, "Bodyless", Map.of("tags", "single")));

        assertThat(entities.load("article", 11L))
                .hasValueSatisfying(loaded -> assertThat(loaded.fields()).containsOnlyKeys("tags"));
    }

    @Test
    void anUnversionedEntityRoundTripsItsFieldValues() {
        entities.save(EntityData.of("note", 1L, "reminder", "A note", Map.of("body", "Remember this")));

        assertThat(entities.load("note", 1L)).hasValueSatisfying(loaded -> {
            assertThat(loaded.revisionId()).isNull();
            assertThat(loaded.fields()).containsEntry("body", "Remember this");
        });
    }

    @Test
    void aVersionedEntityInOneLanguageRecordsItsRevision() {
        EntityData saved = entities.save(
                new EntityData("log", 1L, null, null, "An entry", EntityData.DEFAULT_LANGCODE, null, Map.of()));

        assertThat(saved.revisionId()).isEqualTo(1L);
        assertThat(entities.load("log", 1L))
                .hasValueSatisfying(loaded -> assertThat(loaded.revisionId()).isEqualTo(1L));
    }

    @Test
    void anEntityKeepsTheUuidItIsSavedWith() {
        UUID uuid = UUID.randomUUID();
        EntityData carrying = new EntityData("article", 12L, uuid, "story", "Identified",
                EntityData.DEFAULT_LANGCODE, null, Map.of("body", "Text"));

        assertThat(entities.save(carrying).uuid()).isEqualTo(uuid);
    }

    @Test
    void aRowWrittenWithoutARevisionLoadsAsTheDefaultRevision() {
        entityTypeManager.storageFor("log").save(LOG, 2L, Map.of(
                "uuid", UUID.randomUUID(), "label", "Written directly"));

        assertThat(entities.load("log", 2L))
                .hasValueSatisfying(loaded -> assertThat(loaded.revisionId()).isNull());
    }

    @Test
    void aFailureAfterTheWritesRollsBackTheWholeSave() {
        EntityData refused = story(13L, RecordingListener.FAILING_LABEL, Map.of("body", "Text"));

        assertThatThrownBy(() -> entities.save(refused)).isInstanceOf(IllegalStateException.class);

        assertThat(entities.load("article", 13L)).isEmpty();
        assertThat(fieldTableStorage.read(ARTICLE, 13L, EntityData.DEFAULT_LANGCODE, STORY_FIELDS)).isEmpty();
    }

    @Test
    void removingAFieldDropsItsTable() {
        fieldTableStorage.install(NOTE, "scratch");

        fieldTableStorage.uninstall(NOTE, "scratch");

        assertThat(schemaManager.tableExists(FieldTableStorage.tableName(NOTE, "scratch"))).isFalse();
    }

    @TestConfiguration
    static class ArticleTypes {

        @Bean
        EntityTypeProvider articleTypes() {
            return () -> List.of(ARTICLE, ARTICLE_TYPE, MEMO, NOTE, NOTE_TYPE, LOG);
        }

        @Bean
        RecordingListener recordingListener() {
            return new RecordingListener();
        }

        @Bean
        EntityConstraintProvider articleConstraints() {
            return (type, bundle) -> "article".equals(type.id())
                    ? List.of(ConstraintSpec.on("label", LengthConstraint.ID, Map.of("max", 20)))
                    : List.of();
        }
    }

    static class RecordingListener {

        static final String FAILING_LABEL = "refused";

        private final List<EntityEvent.Phase> phases = new ArrayList<>();

        @EventListener
        void record(EntityEvent event) {
            phases.add(event.phase());
            if (event.entity() instanceof EntityData entity && FAILING_LABEL.equals(entity.label())) {
                throw new IllegalStateException("A listener refused this entity");
            }
        }

        List<EntityEvent.Phase> phases() {
            return List.copyOf(phases);
        }

        void clear() {
            phases.clear();
        }
    }
}
