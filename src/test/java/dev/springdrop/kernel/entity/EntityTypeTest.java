package dev.springdrop.kernel.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EntityTypeTest {

    record Widget(long id, String label) {
    }

    private final EntityType widget = EntityType.content("widget", Widget.class);

    @Test
    void aContentTypeStoresItselfInItsOwnTables() {
        assertThat(widget.kind()).isEqualTo(EntityKind.CONTENT);
        assertThat(widget.baseTable()).isEqualTo("widget");
        assertThat(widget.storageHandler()).isEqualTo(ContentEntityStorage.class);
    }

    @Test
    void aContentTypeIsFieldableAndUnversionedByDefault() {
        assertThat(widget.fieldable()).isTrue();
        assertThat(widget.revisionable()).isFalse();
        assertThat(widget.translatable()).isFalse();
    }

    @Test
    void aConfigTypeStoresItselfInTheConfigStore() {
        EntityType widgetType = EntityType.config("widget_type", Widget.class);

        assertThat(widgetType.kind()).isEqualTo(EntityKind.CONFIG);
        assertThat(widgetType.storageHandler()).isEqualTo(ConfigEntityStorage.class);
        assertThat(widgetType.fieldable()).isFalse();
        assertThat(widgetType.baseTable()).isNull();
    }

    @Test
    void aRevisionableTypeGetsARevisionKeyAndATableToMatch() {
        EntityType revisionable = widget.withRevisions();

        assertThat(revisionable.revisionable()).isTrue();
        assertThat(revisionable.keys().revision()).isEqualTo("revision_id");
        assertThat(revisionable.revisionTable()).isEqualTo("widget_revision");
    }

    @Test
    void aTranslatableTypeIsMarkedAsSuch() {
        assertThat(widget.withTranslations().translatable()).isTrue();
    }

    @Test
    void aBundledTypeCarriesItsBundleKeyAndTheTypeThatDefinesItsBundles() {
        EntityType bundled = widget.withBundles("type", "widget_type");

        assertThat(bundled.keys().bundle()).isEqualTo("type");
        assertThat(bundled.bundleEntityType()).isEqualTo("widget_type");
    }

    @Test
    void aTypeCanDeclareItselfUnfieldable() {
        assertThat(widget.withoutFields().fieldable()).isFalse();
    }

    @Test
    void aTypeCanNameItsOwnAccessHandler() {
        assertThat(widget.withAccessHandler(OpenAccessHandler.class).accessHandler())
                .isEqualTo(OpenAccessHandler.class);
    }

    @Test
    void aTypeCarriesTheRoutesThatLinkToIt() {
        EntityType linked = widget.withLinks(Map.of("canonical", "/widget/{widget}"));

        assertThat(linked.links()).containsEntry("canonical", "/widget/{widget}");
    }

    static class OpenAccessHandler implements EntityAccessHandler {

        @Override
        public dev.springdrop.kernel.access.AccessResult check(
                EntityType type,
                Object entity,
                String operation,
                org.springframework.security.core.Authentication authentication) {
            return dev.springdrop.kernel.access.AccessResult.allow();
        }
    }
}
