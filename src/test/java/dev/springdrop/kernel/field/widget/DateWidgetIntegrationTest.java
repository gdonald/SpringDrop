package dev.springdrop.kernel.field.widget;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.datetime.SiteDateSettings;
import dev.springdrop.kernel.entity.BundleDefinition;
import dev.springdrop.kernel.entity.BundleManager;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.DateRangeFieldType;
import dev.springdrop.kernel.field.types.DateTimeFieldType;
import dev.springdrop.kernel.field.widget.types.DateRangeWidget;
import dev.springdrop.kernel.field.widget.types.DateWidget;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.validation.constraints.DateRangeConstraint;
import dev.springdrop.kernel.validation.constraints.DateTimeConstraint;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@SpringBootTest
class DateWidgetIntegrationTest extends AbstractIntegrationTest {

    record Booking(long id, String label) {
    }

    record BookingType(String id, String label) {
    }

    static final EntityType BOOKING = EntityType.content("booking", Booking.class)
            .withBundles("type", "booking_type");

    static final EntityType BOOKING_TYPE = EntityType.config("booking_type", BookingType.class);

    private static final String BUNDLE = "stay";

    /** Two hours ahead of UTC in March, so a conversion that is skipped shows up. */
    private static final String SITE_ZONE = "Europe/Athens";

    @Autowired
    private FieldWidgetManager widgets;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private FormRenderer renderer;

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private BundleManager bundleManager;

    @Autowired
    private ConfigStore configStore;

    @BeforeEach
    void aBundleAndASiteTimezone() {
        entityTypeManager.installStorage("booking");
        bundleManager.save("booking", new BundleDefinition(BUNDLE, "Stay"));
        configStore.save("system.date", new SiteDateSettings(SITE_ZONE));
    }

    @AfterEach
    void removeFieldsBundleAndTimezone() {
        fields.fieldNames("booking", BUNDLE).forEach(field -> fields.deleteStorage("booking", field));
        bundleManager.delete("booking", BUNDLE);
        configStore.save("system.date", new SiteDateSettings("UTC"));
    }

    private WidgetContext attach(String field, String fieldTypeId, String datetimeType) {
        fields.createStorage(new FieldStorageConfig(field, "booking", fieldTypeId, 1,
                Map.of(DateTimeConstraint.TYPE_OPTION, datetimeType)));
        fields.createInstance(FieldInstanceConfig.of(field, "booking", BUNDLE, field));
        return widgets.context("booking", BUNDLE, field);
    }

    private Document render(WidgetContext context, List<Object> values) {
        return Jsoup.parseBodyFragment(renderer.render(widgets.build(context, values, 0)));
    }

    private Document render(WidgetContext context, List<Object> values, Map<String, String> errors) {
        return Jsoup.parseBodyFragment(renderer.render(widgets.build(context, values, 0), errors));
    }

    @Test
    void aDateOnlyFieldGetsANativeDateInput() {
        WidgetContext context = attach("born_on", DateTimeFieldType.ID, DateTimeConstraint.DATE_ONLY);

        assertThat(render(context, List.of("1996-07-04")).selectFirst("input[type=date]").attr("value"))
                .isEqualTo("1996-07-04");
    }

    @Test
    void aDateOnlyFieldStoresWhatWasTypedWithoutConverting() {
        WidgetContext context = attach("born_on", DateTimeFieldType.ID, DateTimeConstraint.DATE_ONLY);

        assertThat(widgets.extract(context, Map.of("born_on", "1996-07-04"))).containsExactly("1996-07-04");
    }

    @Test
    void aMomentIsShownAsTheWallClockReadingOfTheSiteTimezone() {
        WidgetContext context = attach("starts_at", DateTimeFieldType.ID, DateTimeConstraint.DATE_AND_TIME);

        Document document = render(context, List.of("2026-03-05T14:30:00Z"));

        assertThat(document.selectFirst("input[type=datetime-local]").attr("value"))
                .isEqualTo("2026-03-05T16:30");
    }

    @Test
    void aMomentTypedInIsReadInTheSiteTimezone() {
        WidgetContext context = attach("starts_at", DateTimeFieldType.ID, DateTimeConstraint.DATE_AND_TIME);

        List<Object> values = widgets.extract(context, Map.of("starts_at", "2026-03-05T16:30"));

        assertThat(values).containsExactly("2026-03-05T16:30+02:00");
    }

    @Test
    void anEmptyMomentIsLeftUnset() {
        WidgetContext context = attach("starts_at", DateTimeFieldType.ID, DateTimeConstraint.DATE_AND_TIME);

        assertThat(widgets.extract(context, Map.of("starts_at", "  "))).isEmpty();
        assertThat(render(context, List.of()).selectFirst("input").attr("value")).isEmpty();
    }

    @Test
    void aStoredValueThatIsNotAMomentIsShownAsItStands() {
        WidgetContext context = attach("starts_at", DateTimeFieldType.ID, DateTimeConstraint.DATE_AND_TIME);

        assertThat(render(context, List.of("not a moment")).selectFirst("input").attr("value"))
                .isEqualTo("not a moment");
    }

    @Test
    void aTypedValueThatIsNotAMomentIsKeptForTheServerToReject() {
        WidgetContext context = attach("starts_at", DateTimeFieldType.ID, DateTimeConstraint.DATE_AND_TIME);

        assertThat(widgets.extract(context, Map.of("starts_at", "next Tuesday")))
                .containsExactly("next Tuesday");
    }

    @Test
    void aRangeGetsOneInputForEachOfItsEnds() {
        WidgetContext context = attach("runs", DateRangeFieldType.ID, DateTimeConstraint.DATE_AND_TIME);

        Document document = render(context, List.of(Map.of(
                DateRangeConstraint.START, "2026-03-05T19:00:00Z",
                DateRangeConstraint.END, "2026-03-05T22:00:00Z")));

        assertThat(document.select("input[type=datetime-local]")).extracting(input -> input.attr("name"))
                .containsExactly("runs:start", "runs:end");
        assertThat(document.select("input")).extracting(input -> input.attr("value"))
                .containsExactly("2026-03-05T21:00", "2026-03-06T00:00");
    }

    @Test
    void aRangeIsReadBackAsItsTwoEnds() {
        WidgetContext context = attach("runs", DateRangeFieldType.ID, DateTimeConstraint.DATE_AND_TIME);

        List<Object> values = widgets.extract(context, Map.of(
                "runs:start", "2026-03-05T21:00",
                "runs:end", "2026-03-06T00:00"));

        assertThat(values).containsExactly(Map.of(
                DateRangeConstraint.START, "2026-03-05T21:00+02:00",
                DateRangeConstraint.END, "2026-03-06T00:00+02:00"));
    }

    @Test
    void aDateOnlyRangeKeepsItsEndsAsDates() {
        WidgetContext context = attach("season", DateRangeFieldType.ID, DateTimeConstraint.DATE_ONLY);

        Document document = render(context, List.of(Map.of(
                DateRangeConstraint.START, "2026-06-01",
                DateRangeConstraint.END, "2026-08-31")));

        assertThat(document.select("input[type=date]")).hasSize(2);
        assertThat(widgets.extract(context, Map.of("season:start", "2026-06-01", "season:end", "2026-08-31")))
                .containsExactly(Map.of(
                        DateRangeConstraint.START, "2026-06-01",
                        DateRangeConstraint.END, "2026-08-31"));
    }

    @Test
    void aRangeLeftEmptyIsUnset() {
        WidgetContext context = attach("runs", DateRangeFieldType.ID, DateTimeConstraint.DATE_AND_TIME);

        assertThat(widgets.extract(context, Map.of("runs:start", "", "runs:end", ""))).isEmpty();
    }

    @Test
    void aHalfFilledRangeIsKeptForTheServerToReject() {
        WidgetContext context = attach("runs", DateRangeFieldType.ID, DateTimeConstraint.DATE_AND_TIME);

        assertThat(widgets.extract(context, Map.of("runs:start", "2026-03-05T21:00", "runs:end", "")))
                .containsExactly(Map.of(
                        DateRangeConstraint.START, "2026-03-05T21:00+02:00",
                        DateRangeConstraint.END, ""));
    }

    @Test
    void aRangeWithOnlyAnEndIsKeptForTheServerToReject() {
        WidgetContext context = attach("runs", DateRangeFieldType.ID, DateTimeConstraint.DATE_AND_TIME);

        assertThat(widgets.extract(context, Map.of("runs:start", "", "runs:end", "2026-03-06T00:00")))
                .containsExactly(Map.of(
                        DateRangeConstraint.START, "",
                        DateRangeConstraint.END, "2026-03-06T00:00+02:00"));
    }

    @Test
    void anInvertedRangeShowsItsErrorAgainstThePairOfEnds() {
        WidgetContext context = attach("runs", DateRangeFieldType.ID, DateTimeConstraint.DATE_AND_TIME);

        Document document = render(context, List.of(),
                Map.of("runs", "The end of this range comes before its start."));

        assertThat(document.selectFirst("fieldset .invalid-feedback").text())
                .isEqualTo("The end of this range comes before its start.");
    }

    @Test
    void aRangeWithNoValueYetStillOffersItsTwoEnds() {
        WidgetContext context = attach("runs", DateRangeFieldType.ID, DateTimeConstraint.DATE_AND_TIME);

        assertThat(render(context, List.of()).select("input")).hasSize(2);
    }

    @Test
    void aMomentThatWasNotSubmittedAtAllIsUnset() {
        WidgetContext context = attach("starts_at", DateTimeFieldType.ID, DateTimeConstraint.DATE_AND_TIME);

        assertThat(widgets.extract(context, Map.of())).isEmpty();
    }

    @Test
    void aRangeThatWasNotSubmittedAtAllIsUnset() {
        WidgetContext context = attach("runs", DateRangeFieldType.ID, DateTimeConstraint.DATE_AND_TIME);

        assertThat(widgets.extract(context, Map.of())).isEmpty();
    }

    @Test
    void onlyTheFirstRangeCarriesTheFieldLabel() {
        fields.createStorage(new FieldStorageConfig("runs", "booking", DateRangeFieldType.ID,
                FieldStorageConfig.UNLIMITED,
                Map.of(DateTimeConstraint.TYPE_OPTION, DateTimeConstraint.DATE_AND_TIME)));
        fields.createInstance(FieldInstanceConfig.of("runs", "booking", BUNDLE, "Runs"));
        WidgetContext context = widgets.context("booking", BUNDLE, "runs");

        Document document = render(context, List.of(
                Map.of(DateRangeConstraint.START, "2026-03-05T19:00:00Z",
                        DateRangeConstraint.END, "2026-03-05T22:00:00Z"),
                Map.of(DateRangeConstraint.START, "2026-03-06T19:00:00Z",
                        DateRangeConstraint.END, "2026-03-06T22:00:00Z")));

        assertThat(document.select("fieldset legend")).extracting(element -> element.text())
                .containsExactly("Runs", "");
    }

    @Test
    void eachDateFieldReachesTheWidgetItAsksFor() {
        assertThat(widgets.widget(attach("starts_at", DateTimeFieldType.ID, DateTimeConstraint.DATE_AND_TIME))
                .id()).isEqualTo(DateWidget.ID);
        assertThat(widgets.widget(attach("runs", DateRangeFieldType.ID, DateTimeConstraint.DATE_AND_TIME))
                .id()).isEqualTo(DateRangeWidget.ID);
    }

    @TestConfiguration
    static class BookingTypes {

        @Bean
        EntityTypeProvider bookingTypes() {
            return () -> List.of(BOOKING, BOOKING_TYPE);
        }
    }
}
