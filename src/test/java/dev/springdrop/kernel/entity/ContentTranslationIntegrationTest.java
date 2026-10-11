package dev.springdrop.kernel.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ContentTranslationIntegrationTest extends AbstractIntegrationTest {

    private static final String ARTICLE = "article";

    private static final String SUMMARY = "summary";

    private static final String CODE = "code";

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private NodeService nodes;

    @Autowired
    private NodeTypeManager types;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private TranslatableFields translatableFields;

    @Autowired
    private UserAccountService accounts;

    @Autowired
    private FieldTableStorage fieldTables;

    private long id;

    @BeforeEach
    void anArticleWithATranslatedAndASharedField() {
        clear();
        types.save(NodeType.of(ARTICLE, "Article"));
        for (String field : List.of(SUMMARY, CODE)) {
            fields.createStorage(FieldStorageConfig.single(field, NodeEntityType.ID, StringFieldType.ID));
            fields.createInstance(FieldInstanceConfig.of(field, NodeEntityType.ID, ARTICLE, field));
        }
        translatableFields.set(NodeEntityType.ID, SUMMARY, true);
        id = ((Number) nodes.save(EntityData.of(NodeEntityType.ID, null, ARTICLE, "Spring schedule",
                Map.of(SUMMARY, "Opening hours for spring", CODE, "A1", "status", true, "owner", 1L)), 1L).id())
                .longValue();
    }

    @AfterEach
    void nothingLeft() {
        clear();
    }

    private void clear() {
        queries.query(NodeEntityType.ID).ids().forEach(each -> nodes.delete(((Number) each).longValue()));
        List.of(SUMMARY, CODE).forEach(field -> fields.findStorage(NodeEntityType.ID, field)
                .ifPresent(storage -> fields.deleteStorage(NodeEntityType.ID, field)));
        translatableFields.set(NodeEntityType.ID, SUMMARY, false);
        types.all().forEach(type -> types.delete(type.id()));
    }

    private EntityData load(String langcode) {
        return entities.load(NodeEntityType.ID, id, langcode, null).orElseThrow();
    }

    private void translateIntoFrench() {
        EntityData english = load("en");
        Map<String, Object> values = new LinkedHashMap<>(english.fields());
        values.put(SUMMARY, "Horaires du printemps");
        values.put(CODE, "B2");
        nodes.save(new EntityData(NodeEntityType.ID, id, english.uuid(), ARTICLE, "Printemps", "fr", null, values),
                1L);
    }

    @Test
    void translationsHoldTheirOwnTranslatedFieldsWhileSharedFieldsStaySynchronized() {
        translateIntoFrench();

        EntityData english = load("en");
        EntityData french = load("fr");
        assertThat(english.label()).isEqualTo("Spring schedule");
        assertThat(english.fields()).containsEntry(SUMMARY, "Opening hours for spring").containsEntry(CODE, "B2");
        assertThat(french.label()).isEqualTo("Printemps");
        assertThat(french.langcode()).isEqualTo("fr");
        assertThat(french.fields()).containsEntry(SUMMARY, "Horaires du printemps").containsEntry(CODE, "B2");
        assertThat(entities.translations(NodeEntityType.ID, id)).containsExactly("en", "fr");

        Map<String, Object> englishValues = new LinkedHashMap<>(english.fields());
        englishValues.put(CODE, "C3");
        nodes.save(english.withFields(englishValues), 1L);
        assertThat(load("fr").fields()).containsEntry(CODE, "C3").containsEntry(SUMMARY, "Horaires du printemps");
        assertThat(load("fr").label()).isEqualTo("Printemps");
    }

    @Test
    void aLanguageWithoutATranslationReadsTheEntityInItsOwn() {
        EntityData german = load("de");

        assertThat(german.langcode()).isEqualTo("en");
        assertThat(german.label()).isEqualTo("Spring schedule");
        assertThat(entities.translations("node", 999_999L)).isEmpty();
    }

    @Test
    void aQueryInALanguageFindsEntitiesInItAndReadsTheirTranslatedFieldsInIt() {
        translateIntoFrench();

        assertThat(queries.query(NodeEntityType.ID).inLanguage("fr")
                .condition(Condition.equal(SUMMARY, "Horaires du printemps")).ids()).containsExactly(id);
        assertThat(queries.query(NodeEntityType.ID).inLanguage("fr")
                .condition(Condition.equal(CODE, "B2")).ids()).containsExactly(id);
        assertThat(queries.query(NodeEntityType.ID).inLanguage("en")
                .condition(Condition.equal(SUMMARY, "Horaires du printemps")).ids()).isEmpty();
        assertThat(queries.query(NodeEntityType.ID).inLanguage("de").ids()).isEmpty();
        assertThat(queries.query(NodeEntityType.ID).condition(Condition.equal(SUMMARY, "Opening hours for spring"))
                .ids()).containsExactly(id);
    }

    @Test
    void deletingATranslationLeavesTheEntityInItsOwnLanguage() {
        translateIntoFrench();

        entities.deleteTranslation(NodeEntityType.ID, id, "fr");

        assertThat(entities.translations(NodeEntityType.ID, id)).containsExactly("en");
        assertThat(load("fr").label()).isEqualTo("Spring schedule");
        assertThat(load("fr").fields()).containsEntry(SUMMARY, "Opening hours for spring");
        assertThatThrownBy(() -> entities.deleteTranslation(NodeEntityType.ID, id, "en"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deletingTheEntityDeletesItsTranslations() {
        translateIntoFrench();

        nodes.delete(id);

        assertThat(entities.translations(NodeEntityType.ID, id)).isEmpty();
        assertThat(queries.query(NodeEntityType.ID).inLanguage("fr").ids()).isEmpty();
    }

    @Test
    void anEarlierRevisionIsReadWithItsTranslatedAndSharedFields() {
        long firstRevision = load("en").revisionId();
        translateIntoFrench();

        EntityData earlier = entities.load(NodeEntityType.ID, id, "en", firstRevision).orElseThrow();

        assertThat(earlier.fields()).containsEntry(CODE, "A1").containsEntry(SUMMARY, "Opening hours for spring");
    }

    @Test
    void aLanguagesValuesAreDeletedFromATypeWithoutRevisions() {
        EntityType phrase = EntityType.content("phrase", EntityData.class).withTranslations();
        fieldTables.install(phrase, "text");
        try {
            fieldTables.write(phrase, 1L, null, "fr", "text", "Bonjour");
            fieldTables.write(phrase, 1L, null, "en", "text", "Hello");

            fieldTables.deleteLanguage(phrase, 1L, "fr", List.of("text"));

            assertThat(fieldTables.read(phrase, 1L, "fr", List.of("text"))).isEmpty();
            assertThat(fieldTables.read(phrase, 1L, "en", List.of("text"))).containsEntry("text", "Hello");
        } finally {
            fieldTables.uninstall(phrase, "text");
        }
    }

    @Test
    void aTypeThatIsNotTranslatableHasNoTranslationsToDelete() {
        accounts.install();

        assertThatThrownBy(() -> entities.deleteTranslation("user", 1L, "fr"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(entities.translations("user", 1L)).containsExactly("en");
    }
}
