package dev.springdrop.kernel.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.springdrop.kernel.schema.ColumnType;
import dev.springdrop.kernel.schema.SchemaManager;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.TestingAuthenticationToken;

@SpringBootTest
class EntityStorageIntegrationTest extends AbstractIntegrationTest {

    record Gadget(long id, String label) {
    }

    record GadgetType(String id, String label) {
    }

    static final EntityType GADGET = EntityType.content("gadget", Gadget.class)
            .withRevisions()
            .withTranslations()
            .withBundles("type", "gadget_type");

    static final EntityType GADGET_TYPE = EntityType.config("gadget_type", GadgetType.class);

    /** A content type with no bundle, no revisions, one language, and a jsonb base field. */
    static final EntityType TRINKET = EntityType.content("trinket", Gadget.class)
            .withBaseFields(List.of(BaseFieldDefinition.optional("tags", ColumnType.JSONB)));

    /** A revisionable type in one language. */
    static final EntityType SPROCKET = EntityType.content("sprocket", Gadget.class).withRevisions();

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private SchemaManager schemaManager;

    @Autowired
    private ContentEntityStorage contentStorage;

    @Test
    void aContentTypeCreatesItsBaseAndRevisionTables() {
        entityTypeManager.installStorage("gadget");

        assertThat(schemaManager.tableExists("gadget")).isTrue();
        assertThat(schemaManager.tableExists("gadget_revision")).isTrue();
        assertThat(schemaManager.columnExists("gadget", "type")).isTrue();
        assertThat(schemaManager.columnExists("gadget", "langcode")).isTrue();
        assertThat(schemaManager.columnExists("gadget", "revision_id")).isTrue();
    }

    @Test
    void aContentEntityRoundTripsThroughItsBaseTable() {
        entityTypeManager.installStorage("gadget");
        EntityStorage storage = entityTypeManager.storageFor("gadget");

        storage.save(GADGET, 1L, Map.of(
                "uuid", UUID.randomUUID(), "label", "First gadget", "type", "standard", "langcode", "en"));

        assertThat(storage.load(GADGET, 1L)).hasValueSatisfying(values ->
                assertThat(values).containsEntry("label", "First gadget").containsEntry("type", "standard"));
    }

    @Test
    void savingAnExistingContentEntityUpdatesItInPlace() {
        entityTypeManager.installStorage("gadget");
        EntityStorage storage = entityTypeManager.storageFor("gadget");
        Map<String, Object> values = Map.of(
                "uuid", UUID.randomUUID(), "label", "Before", "type", "standard", "langcode", "en");

        storage.save(GADGET, 2L, values);
        storage.save(GADGET, 2L, Map.of(
                "uuid", values.get("uuid"), "label", "After", "type", "standard", "langcode", "en"));

        assertThat(storage.load(GADGET, 2L)).hasValueSatisfying(loaded ->
                assertThat(loaded).containsEntry("label", "After"));
    }

    @Test
    void deletingAContentEntityRemovesItsRow() {
        entityTypeManager.installStorage("gadget");
        EntityStorage storage = entityTypeManager.storageFor("gadget");
        storage.save(GADGET, 3L, Map.of(
                "uuid", UUID.randomUUID(), "label", "Doomed", "type", "standard", "langcode", "en"));

        storage.delete(GADGET, 3L);

        assertThat(storage.load(GADGET, 3L)).isEmpty();
    }

    @Test
    void aBaseFieldStoredAsJsonKeepsItsShapeAcrossARoundTrip() {
        entityTypeManager.installStorage("trinket");
        EntityStorage storage = entityTypeManager.storageFor("trinket");

        storage.save(TRINKET, 4L, Map.of(
                "uuid", UUID.randomUUID(), "label", "Tagged", "tags", List.of("one", "two")));

        assertThat(storage.load(TRINKET, 4L)).hasValueSatisfying(values ->
                assertThat(contentStorage.javaValue(TRINKET, "tags", values.get("tags")))
                        .isEqualTo(List.of("one", "two")));
    }

    @Test
    void aValueThatIsNotJsonYetIsReadBackAsItStands() {
        assertThat(contentStorage.javaValue(TRINKET, "tags", "not json yet")).isEqualTo("not json yet");
        assertThat(contentStorage.javaValue(TRINKET, "label", "plain")).isEqualTo("plain");
    }

    @Test
    void aPlainContentTypeCreatesOnlyItsBaseTable() {
        entityTypeManager.installStorage("trinket");

        assertThat(schemaManager.tableExists("trinket")).isTrue();
        assertThat(schemaManager.tableExists("trinket_revision")).isFalse();
        assertThat(schemaManager.columnExists("trinket", "langcode")).isFalse();
    }

    @Test
    void aPlainContentEntityRoundTripsAndDeletes() {
        entityTypeManager.installStorage("trinket");
        EntityStorage storage = entityTypeManager.storageFor("trinket");
        storage.save(TRINKET, 1L, Map.of("uuid", UUID.randomUUID(), "label", "Trinket"));

        assertThat(storage.load(TRINKET, 1L)).isPresent();

        storage.delete(TRINKET, 1L);

        assertThat(storage.load(TRINKET, 1L)).isEmpty();
    }

    @Test
    void uninstallingAPlainContentTypeDropsItsOnlyTable() {
        entityTypeManager.installStorage("trinket");

        entityTypeManager.uninstallStorage("trinket");

        assertThat(schemaManager.tableExists("trinket")).isFalse();
    }

    @Test
    void anUntranslatedRevisionTableHasNoLanguageColumn() {
        entityTypeManager.installStorage("sprocket");

        assertThat(schemaManager.tableExists("sprocket_revision")).isTrue();
        assertThat(schemaManager.columnExists("sprocket_revision", "langcode")).isFalse();
    }

    @Test
    void aConfigEntityPersistsThroughTheConfigStore() {
        entityTypeManager.installStorage("gadget_type");
        EntityStorage storage = entityTypeManager.storageFor("gadget_type");

        storage.save(GADGET_TYPE, "standard", Map.of("label", "Standard gadget"));

        assertThat(storage.load(GADGET_TYPE, "standard")).hasValueSatisfying(values -> assertThat(values)
                .containsEntry("label", "Standard gadget")
                .containsEntry("id", "standard"));
    }

    @Test
    void aDeletedConfigEntityIsGoneFromTheConfigStore() {
        EntityStorage storage = entityTypeManager.storageFor("gadget_type");
        storage.save(GADGET_TYPE, "doomed", Map.of("label", "Doomed"));

        storage.delete(GADGET_TYPE, "doomed");

        assertThat(storage.load(GADGET_TYPE, "doomed")).isEmpty();
    }

    @Test
    void uninstallingAContentTypeDropsItsTables() {
        entityTypeManager.installStorage("gadget");

        entityTypeManager.uninstallStorage("gadget");

        assertThat(schemaManager.tableExists("gadget")).isFalse();
        assertThat(schemaManager.tableExists("gadget_revision")).isFalse();
    }

    @Test
    void uninstallingAConfigTypeLeavesTheConfigStoreAlone() {
        EntityStorage storage = entityTypeManager.storageFor("gadget_type");
        storage.save(GADGET_TYPE, "kept", Map.of("label", "Kept"));

        entityTypeManager.uninstallStorage("gadget_type");

        assertThat(storage.load(GADGET_TYPE, "kept")).isPresent();
    }

    @Test
    void theRegistryFindsARegisteredTypeAndRejectsAnUnknownOne() {
        assertThat(entityTypeManager.find("gadget")).contains(GADGET);
        assertThat(entityTypeManager.find("nonesuch")).isEmpty();
        assertThat(entityTypeManager.all()).contains(GADGET, GADGET_TYPE);
        assertThatThrownBy(() -> entityTypeManager.require("nonesuch"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonesuch");
    }

    @Test
    void aTypeIsAdministeredByWhoeverHoldsItsAdministerPermission() {
        EntityAccessHandler handler = entityTypeManager.accessHandlerFor("gadget");
        var administrator = new TestingAuthenticationToken("admin", "password", "administer gadget");
        var editor = new TestingAuthenticationToken("editor", "password", "edit gadget");

        assertThat(handler.check(GADGET, null, EntityAccessHandler.UPDATE, administrator).allowed()).isTrue();
        assertThat(handler.check(GADGET, null, EntityAccessHandler.UPDATE, editor).allowed()).isFalse();
        assertThat(handler.check(GADGET, null, EntityAccessHandler.VIEW, null).allowed()).isFalse();
    }

    @Test
    void anAccessDecisionAboutAnEntityVariesByPermissions() {
        EntityAccessHandler handler = entityTypeManager.accessHandlerFor("gadget");
        var administrator = new TestingAuthenticationToken("admin", "password", "administer gadget");

        assertThat(handler.check(GADGET, null, EntityAccessHandler.DELETE, administrator).cacheContexts())
                .containsExactly("user.permissions");
    }

    @TestConfiguration
    static class TestEntityTypes {

        @Bean
        EntityTypeProvider gadgetTypes() {
            return () -> List.of(GADGET, GADGET_TYPE, TRINKET, SPROCKET);
        }
    }
}
