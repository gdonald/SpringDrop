package dev.springdrop.kernel.field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.springdrop.kernel.datetime.DateFormatService;
import dev.springdrop.kernel.entity.BundleDefinition;
import dev.springdrop.kernel.entity.BundleManager;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.entity.EntityValidationException;
import dev.springdrop.kernel.field.types.DateRangeFieldType;
import dev.springdrop.kernel.field.types.DateTimeFieldType;
import dev.springdrop.kernel.field.types.TimestampFieldType;
import dev.springdrop.kernel.validation.constraints.DateRangeConstraint;
import dev.springdrop.kernel.validation.constraints.DateTimeConstraint;
import dev.springdrop.support.AbstractIntegrationTest;
import java.time.Instant;
import java.time.OffsetDateTime;
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
class DateFieldTypeIntegrationTest extends AbstractIntegrationTest {

    record Event(long id, String label) {
    }

    record EventType(String id, String label) {
    }

    static final EntityType EVENT = EntityType.content("event", Event.class)
            .withBundles("type", "event_type");

    static final EntityType EVENT_TYPE = EntityType.config("event_type", EventType.class);

    private static final String BUNDLE = "concert";

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private BundleManager bundleManager;

    @Autowired
    private DateFormatService dateFormatService;

    @BeforeEach
    void aBundleToAttachDateFieldsTo() {
        entityTypeManager.installStorage("event");
        bundleManager.save("event", new BundleDefinition(BUNDLE, "Concert"));
    }

    @AfterEach
    void removeFieldsAndBundle() {
        fields.fieldNames("event", BUNDLE).forEach(field -> fields.deleteStorage("event", field));
        bundleManager.delete("event", BUNDLE);
    }

    private void attach(String field, String fieldTypeId, Map<String, Object> storageSettings) {
        fields.createStorage(new FieldStorageConfig(field, "event", fieldTypeId, 1, storageSettings));
        fields.createInstance(FieldInstanceConfig.of(field, "event", BUNDLE, field));
    }

    private void save(long id, Map<String, Object> values) {
        entities.save(EntityData.of("event", id, BUNDLE, "A concert", values));
    }

    private Map<String, Object> loadedFields(long id) {
        return entities.load("event", id).orElseThrow().fields();
    }

    @Test
    void aTimestampFieldStoresAMomentAsSeconds() {
        attach("published_at", TimestampFieldType.ID, Map.of());
        long seconds = Instant.parse("2026-03-05T14:30:00Z").getEpochSecond();

        save(1L, Map.of("published_at", seconds));

        assertThat(((Number) loadedFields(1L).get("published_at")).longValue()).isEqualTo(seconds);
    }

    @Test
    void aDateAndTimeFieldRoundTripsInTheSiteTimezone() {
        attach("starts_at", DateTimeFieldType.ID, Map.of());

        save(2L, Map.of("starts_at", "2026-03-05T14:30:00Z"));

        String stored = loadedFields(2L).get("starts_at").toString();
        assertThat(dateFormatService.format(OffsetDateTime.parse(stored).toInstant(), "html_date", null))
                .isEqualTo("2026-03-05");
    }

    @Test
    void aDateAndTimeFieldRejectsAValueThatIsNotADateAndTime() {
        attach("starts_at", DateTimeFieldType.ID, Map.of());

        assertThatThrownBy(() -> save(3L, Map.of("starts_at", "next Tuesday")))
                .isInstanceOf(EntityValidationException.class);
    }

    @Test
    void aDateOnlyFieldStoresADateWithoutATimeOfDay() {
        attach("born_on", DateTimeFieldType.ID,
                Map.of(DateTimeConstraint.TYPE_OPTION, DateTimeConstraint.DATE_ONLY));

        save(4L, Map.of("born_on", "1996-07-04"));

        assertThat(loadedFields(4L)).containsEntry("born_on", "1996-07-04");
    }

    @Test
    void aDateOnlyFieldRejectsAValueCarryingATimeOfDay() {
        attach("born_on", DateTimeFieldType.ID,
                Map.of(DateTimeConstraint.TYPE_OPTION, DateTimeConstraint.DATE_ONLY));

        assertThatThrownBy(() -> save(5L, Map.of("born_on", "1996-07-04T10:00:00Z")))
                .isInstanceOf(EntityValidationException.class);
    }

    @Test
    void aDateRangeStoresItsStartAndEnd() {
        attach("runs", DateRangeFieldType.ID, Map.of());

        save(6L, Map.of("runs", Map.of(
                DateRangeConstraint.START, "2026-03-05T19:00:00Z",
                DateRangeConstraint.END, "2026-03-05T22:00:00Z")));

        assertThat(loadedFields(6L).get("runs")).isEqualTo(Map.of(
                DateRangeConstraint.START, "2026-03-05T19:00:00Z",
                DateRangeConstraint.END, "2026-03-05T22:00:00Z"));
    }

    @Test
    void aDateRangeRejectsAnEndBeforeItsStart() {
        attach("runs", DateRangeFieldType.ID, Map.of());

        assertThatThrownBy(() -> save(7L, Map.of("runs", Map.of(
                DateRangeConstraint.START, "2026-03-05T22:00:00Z",
                DateRangeConstraint.END, "2026-03-05T19:00:00Z"))))
                .isInstanceOf(EntityValidationException.class);
    }

    @Test
    void aDateRangeRejectsAHalfFilledRange() {
        attach("runs", DateRangeFieldType.ID, Map.of());

        assertThatThrownBy(() -> save(8L, Map.of("runs",
                Map.of(DateRangeConstraint.START, "2026-03-05T19:00:00Z"))))
                .isInstanceOf(EntityValidationException.class);
    }

    @Test
    void aDateRangeRejectsAValueThatIsNotARange() {
        attach("runs", DateRangeFieldType.ID, Map.of());

        assertThatThrownBy(() -> save(9L, Map.of("runs", "all evening")))
                .isInstanceOf(EntityValidationException.class);
    }

    @Test
    void aDateRangeRejectsEndsThatAreNotDates() {
        attach("runs", DateRangeFieldType.ID, Map.of());

        assertThatThrownBy(() -> save(10L, Map.of("runs", Map.of(
                DateRangeConstraint.START, "tonight",
                DateRangeConstraint.END, "later"))))
                .isInstanceOf(EntityValidationException.class);
    }

    @Test
    void aDateOnlyRangeComparesItsEndsAsDates() {
        attach("season", DateRangeFieldType.ID,
                Map.of(DateTimeConstraint.TYPE_OPTION, DateTimeConstraint.DATE_ONLY));

        save(11L, Map.of("season", Map.of(
                DateRangeConstraint.START, "2026-06-01",
                DateRangeConstraint.END, "2026-08-31")));

        assertThat(loadedFields(11L)).containsKey("season");
        assertThatThrownBy(() -> save(12L, Map.of("season", Map.of(
                DateRangeConstraint.START, "2026-08-31",
                DateRangeConstraint.END, "2026-06-01"))))
                .isInstanceOf(EntityValidationException.class);
    }

    @Test
    void everyDateTypeDeclaresItsWidgetAndFormatter() {
        List<String> ids = List.of(TimestampFieldType.ID, DateTimeFieldType.ID, DateRangeFieldType.ID);

        assertThat(ids).allSatisfy(id -> {
            FieldType type = fields.fieldType(id);
            assertThat(type.id()).isEqualTo(id);
            assertThat(type.properties()).isNotEmpty();
            assertThat(type.defaultWidget()).isNotBlank();
            assertThat(type.defaultFormatter()).isNotBlank();
            assertThat(type.defaultStorageSettings()).isNotNull();
            assertThat(type.defaultInstanceSettings()).isEmpty();
        });
    }

    @TestConfiguration
    static class EventTypes {

        @Bean
        EntityTypeProvider eventTypes() {
            return () -> List.of(EVENT, EVENT_TYPE);
        }
    }
}
