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
import dev.springdrop.kernel.field.types.EmailFieldType;
import dev.springdrop.kernel.field.types.LinkFieldType;
import dev.springdrop.kernel.field.types.TelephoneFieldType;
import dev.springdrop.kernel.routing.RouteDefinition;
import dev.springdrop.kernel.routing.RouteRegistrar;
import dev.springdrop.kernel.validation.constraints.LinkConstraint;
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
class ContactFieldTypeIntegrationTest extends AbstractIntegrationTest {

    record Contact(long id, String label) {
    }

    record ContactType(String id, String label) {
    }

    static final EntityType CONTACT = EntityType.content("contact", Contact.class)
            .withBundles("type", "contact_type");

    static final EntityType CONTACT_TYPE = EntityType.config("contact_type", ContactType.class);

    private static final String BUNDLE = "person";

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private BundleManager bundleManager;

    @Autowired
    private LinkResolver linkResolver;

    @BeforeEach
    void aBundleToAttachContactFieldsTo() {
        entityTypeManager.installStorage("contact");
        bundleManager.save("contact", new BundleDefinition(BUNDLE, "Person"));
    }

    @AfterEach
    void removeFieldsAndBundle() {
        fields.fieldNames("contact", BUNDLE).forEach(field -> fields.deleteStorage("contact", field));
        bundleManager.delete("contact", BUNDLE);
    }

    private void attach(String field, String fieldTypeId) {
        fields.createStorage(FieldStorageConfig.single(field, "contact", fieldTypeId));
        fields.createInstance(FieldInstanceConfig.of(field, "contact", BUNDLE, field));
    }

    private void save(long id, Map<String, Object> values) {
        entities.save(EntityData.of("contact", id, BUNDLE, "A person", values));
    }

    private Map<String, Object> loadedFields(long id) {
        return entities.load("contact", id).orElseThrow().fields();
    }

    @Test
    void anEmailAddressPersists() {
        attach("mail", EmailFieldType.ID);

        save(1L, Map.of("mail", "alice@example.com"));

        assertThat(loadedFields(1L)).containsEntry("mail", "alice@example.com");
    }

    @Test
    void anInvalidEmailAddressIsRejected() {
        attach("mail", EmailFieldType.ID);

        assertThatThrownBy(() -> save(2L, Map.of("mail", "alice at example")))
                .isInstanceOf(EntityValidationException.class);
    }

    @Test
    void aTelephoneNumberPersists() {
        attach("phone", TelephoneFieldType.ID);

        save(3L, Map.of("phone", "+1 (555) 123-4567"));

        assertThat(loadedFields(3L)).containsEntry("phone", "+1 (555) 123-4567");
    }

    @Test
    void anInvalidTelephoneNumberIsRejected() {
        attach("phone", TelephoneFieldType.ID);

        assertThatThrownBy(() -> save(4L, Map.of("phone", "call me")))
                .isInstanceOf(EntityValidationException.class);
    }

    @Test
    void anExternalLinkPersistsWithItsTitle() {
        attach("website", LinkFieldType.ID);
        Map<String, Object> link = Map.of(
                LinkConstraint.URI_KEY, "https://example.com/about",
                LinkConstraint.TITLE_KEY, "About us");

        save(5L, Map.of("website", link));

        assertThat(loadedFields(5L)).containsEntry("website", link);
        assertThat(linkResolver.isExternal(link)).isTrue();
        assertThat(linkResolver.route(link)).isEmpty();
    }

    @Test
    void anInternalLinkResolvesToItsRoute() {
        attach("website", LinkFieldType.ID);
        Map<String, Object> link = Map.of(LinkConstraint.URI_KEY, "internal:/contact-us");

        save(6L, Map.of("website", link));

        assertThat(linkResolver.route(loadedFields(6L).get("website")))
                .hasValueSatisfying(route -> assertThat(route.name()).isEqualTo("contact_page"));
        assertThat(linkResolver.isExternal(link)).isFalse();
    }

    @Test
    void anInternalLinkToNoRouteResolvesToNothing() {
        Map<String, Object> link = Map.of(LinkConstraint.URI_KEY, "internal:/nowhere");

        assertThat(linkResolver.route(link)).isEmpty();
    }

    @Test
    void aValueThatIsNotALinkResolvesToNothing() {
        assertThat(linkResolver.route("https://example.com")).isEmpty();
        assertThat(linkResolver.isExternal("https://example.com")).isFalse();
        assertThat(linkResolver.route(Map.of(LinkConstraint.TITLE_KEY, "No uri here"))).isEmpty();
    }

    @Test
    void anInvalidUriIsRejected() {
        attach("website", LinkFieldType.ID);

        assertThatThrownBy(() -> save(7L, Map.of("website",
                Map.of(LinkConstraint.URI_KEY, "not a uri at all"))))
                .isInstanceOf(EntityValidationException.class);
    }

    @Test
    void anInternalLinkWithoutAPathIsRejected() {
        attach("website", LinkFieldType.ID);

        assertThatThrownBy(() -> save(8L, Map.of("website",
                Map.of(LinkConstraint.URI_KEY, "internal:contact-us"))))
                .isInstanceOf(EntityValidationException.class);
    }

    @Test
    void aLinkNeedsAUri() {
        attach("website", LinkFieldType.ID);

        assertThatThrownBy(() -> save(9L, Map.of("website",
                Map.of(LinkConstraint.TITLE_KEY, "Title only"))))
                .isInstanceOf(EntityValidationException.class);
    }

    @Test
    void aValueThatIsNotALinkIsRejected() {
        attach("website", LinkFieldType.ID);

        assertThatThrownBy(() -> save(10L, Map.of("website", "https://example.com")))
                .isInstanceOf(EntityValidationException.class);
    }

    @Test
    void everyContactTypeDeclaresItsWidgetAndFormatter() {
        List<String> ids = List.of(EmailFieldType.ID, TelephoneFieldType.ID, LinkFieldType.ID);

        assertThat(ids).allSatisfy(id -> {
            FieldType type = fields.fieldType(id);
            assertThat(type.id()).isEqualTo(id);
            assertThat(type.properties()).isNotEmpty();
            assertThat(type.defaultWidget()).isNotBlank();
            assertThat(type.defaultFormatter()).isNotBlank();
            assertThat(type.defaultStorageSettings()).isEmpty();
            assertThat(type.defaultInstanceSettings()).isEmpty();
        });
    }

    @TestConfiguration
    static class ContactTypes {

        @Bean
        EntityTypeProvider contactTypes() {
            return () -> List.of(CONTACT, CONTACT_TYPE);
        }

        @Bean
        RouteRegistrar contactRoutes() {
            return () -> List.of(RouteDefinition.frontEnd("/contact-us", "contact_page", "Contact us"));
        }
    }
}
