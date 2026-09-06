package dev.springdrop.kernel.field.formatter;

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
import dev.springdrop.kernel.field.formatter.types.DateRangeFormatter;
import dev.springdrop.kernel.field.formatter.types.DateTimeFormatter;
import dev.springdrop.kernel.field.formatter.types.LinkFormatter;
import dev.springdrop.kernel.field.formatter.types.TimestampFormatter;
import dev.springdrop.kernel.field.types.DateRangeFieldType;
import dev.springdrop.kernel.field.types.DateTimeFieldType;
import dev.springdrop.kernel.field.types.LinkFieldType;
import dev.springdrop.kernel.field.types.TimestampFieldType;
import dev.springdrop.kernel.validation.constraints.DateRangeConstraint;
import dev.springdrop.kernel.validation.constraints.DateTimeConstraint;
import dev.springdrop.kernel.validation.constraints.LinkConstraint;
import dev.springdrop.support.AbstractIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
import org.springframework.context.annotation.Primary;

@SpringBootTest
class DateAndLinkFormatterIntegrationTest extends AbstractIntegrationTest {

    record Notice(long id, String label) {
    }

    record NoticeType(String id, String label) {
    }

    static final EntityType NOTICE = EntityType.content("notice", Notice.class)
            .withBundles("type", "notice_type");

    static final EntityType NOTICE_TYPE = EntityType.config("notice_type", NoticeType.class);

    private static final String BUNDLE = "bulletin";

    /** A fixed now, so "time ago" says the same thing on every run. */
    static final Instant NOW = Instant.parse("2026-03-05T12:00:00Z");

    @Autowired
    private FieldFormatterManager formatters;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private BundleManager bundleManager;

    @Autowired
    private ConfigStore configStore;

    @BeforeEach
    void aBundleAndASiteTimezone() {
        entityTypeManager.installStorage("notice");
        bundleManager.save("notice", new BundleDefinition(BUNDLE, "Bulletin"));
        configStore.save("system.date", new SiteDateSettings("Europe/Athens"));
    }

    @AfterEach
    void removeFieldsBundleAndTimezone() {
        fields.fieldNames("notice", BUNDLE).forEach(field -> fields.deleteStorage("notice", field));
        bundleManager.delete("notice", BUNDLE);
        configStore.save("system.date", new SiteDateSettings("UTC"));
    }

    private FormatterContext attach(
            String field, String fieldTypeId, Map<String, Object> storageSettings,
            Map<String, Object> formatterSettings) {

        fields.createStorage(new FieldStorageConfig(field, "notice", fieldTypeId, 1, storageSettings));
        fields.createInstance(FieldInstanceConfig.of(field, "notice", BUNDLE, field));
        return formatters.context("notice", BUNDLE, field, formatterSettings);
    }

    private String text(FormatterContext context, Object value) {
        Document document = Jsoup.parseBodyFragment(formatters.render(context, value));
        return document.selectFirst(".field-item").text();
    }

    @Test
    void aMomentReadsInTheSiteTimezone() {
        FormatterContext context = attach("starts_at", DateTimeFieldType.ID,
                Map.of(), Map.of(DateTimeFormatter.DATE_FORMAT, "html_date"));

        assertThat(text(context, "2026-03-05T23:30:00Z")).isEqualTo("2026-03-06");
    }

    @Test
    void aMomentReadsInTheTimezoneTheDisplayNames() {
        FormatterContext context = attach("starts_at", DateTimeFieldType.ID, Map.of(),
                Map.of(DateTimeFormatter.DATE_FORMAT, "html_date", DateTimeFormatter.TIMEZONE, "UTC"));

        assertThat(text(context, "2026-03-05T23:30:00Z")).isEqualTo("2026-03-05");
    }

    @Test
    void aDateOnlyValueReadsAsItsOwnDay() {
        FormatterContext context = attach("born_on", DateTimeFieldType.ID,
                Map.of(DateTimeConstraint.TYPE_OPTION, DateTimeConstraint.DATE_ONLY),
                Map.of(DateTimeFormatter.DATE_FORMAT, "html_date"));

        assertThat(text(context, "1996-07-04")).isEqualTo("1996-07-04");
    }

    @Test
    void aValueThatIsNotAMomentReadsAsItStands() {
        FormatterContext context = attach("starts_at", DateTimeFieldType.ID, Map.of(), Map.of());

        assertThat(text(context, "not a moment")).isEqualTo("not a moment");
    }

    @Test
    void aMomentCanReadAsHowLongAgoItWas() {
        FormatterContext context = attach("starts_at", DateTimeFieldType.ID,
                Map.of(), Map.of(DateTimeFormatter.TIME_AGO, true));

        assertThat(text(context, "2026-03-02T12:00:00Z")).isEqualTo("3 days ago");
        assertThat(text(context, "2026-03-05T14:00:00Z")).isEqualTo("in 2 hours");
    }

    @Test
    void aMomentJustNowReadsAsSuch() {
        FormatterContext context = attach("starts_at", DateTimeFieldType.ID,
                Map.of(), Map.of(DateTimeFormatter.TIME_AGO, true));

        assertThat(text(context, "2026-03-05T12:00:30Z")).isEqualTo("just now");
    }

    @Test
    void howLongAgoCountsInTheLargestUnitThatStillSaysSomething() {
        FormatterContext context = attach("starts_at", DateTimeFieldType.ID,
                Map.of(), Map.of(DateTimeFormatter.TIME_AGO, true));

        assertThat(text(context, "2026-03-05T11:59:00Z")).isEqualTo("1 minute ago");
        assertThat(text(context, "2026-03-05T10:00:00Z")).isEqualTo("2 hours ago");
        assertThat(text(context, "2026-02-01T12:00:00Z")).isEqualTo("1 month ago");
        assertThat(text(context, "2026-01-01T12:00:00Z")).isEqualTo("2 months ago");
        assertThat(text(context, "2023-03-05T12:00:00Z")).isEqualTo("3 years ago");
    }

    @Test
    void theMomentFormatterAnswersToItsOwnName() {
        FormatterContext context = attach("starts_at", DateTimeFieldType.ID, Map.of(), Map.of());

        assertThat(formatters.formatter(context).id()).isEqualTo(DateTimeFormatter.ID);
    }

    @Test
    void aTimestampReadsInANamedFormat() {
        FormatterContext context = attach("published_at", TimestampFieldType.ID,
                Map.of(), Map.of(DateTimeFormatter.DATE_FORMAT, "html_date"));

        assertThat(text(context, NOW.getEpochSecond())).isEqualTo("2026-03-05");
        assertThat(formatters.formatter(context).id()).isEqualTo(TimestampFormatter.ID);
    }

    @Test
    void aTimestampCanReadAsHowLongAgoItWasOrInAnotherTimezone() {
        FormatterContext ago = attach("published_at", TimestampFieldType.ID,
                Map.of(), Map.of(DateTimeFormatter.TIME_AGO, true));
        assertThat(text(ago, NOW.minusSeconds(7200).getEpochSecond())).isEqualTo("2 hours ago");

        fields.deleteStorage("notice", "published_at");
        FormatterContext elsewhere = attach("published_at", TimestampFieldType.ID, Map.of(),
                Map.of(DateTimeFormatter.DATE_FORMAT, "html_time", DateTimeFormatter.TIMEZONE, "UTC"));
        assertThat(text(elsewhere, NOW.getEpochSecond())).isEqualTo("12:00:00");
    }

    @Test
    void aRangeReadsAsItsTwoEndsJoined() {
        FormatterContext context = attach("runs", DateRangeFieldType.ID,
                Map.of(), Map.of(DateTimeFormatter.DATE_FORMAT, "html_date"));

        String rendered = text(context, Map.of(
                DateRangeConstraint.START, "2026-03-05T19:00:00Z",
                DateRangeConstraint.END, "2026-03-06T19:00:00Z"));

        assertThat(rendered).isEqualTo("2026-03-05 to 2026-03-06");
        assertThat(formatters.formatter(context).id()).isEqualTo(DateRangeFormatter.ID);
    }

    @Test
    void aRangeJoinsItsEndsWithWhateverWordTheDisplayPrefers() {
        FormatterContext context = attach("runs", DateRangeFieldType.ID, Map.of(),
                Map.of(DateTimeFormatter.DATE_FORMAT, "html_date", DateRangeFormatter.SEPARATOR, " until "));

        assertThat(text(context, Map.of(
                DateRangeConstraint.START, "2026-03-05T19:00:00Z",
                DateRangeConstraint.END, "2026-03-06T19:00:00Z")))
                .isEqualTo("2026-03-05 until 2026-03-06");
    }

    @Test
    void aValueThatIsNotARangeReadsAsItStands() {
        FormatterContext context = attach("runs", DateRangeFieldType.ID, Map.of(), Map.of());

        assertThat(text(context, "all evening")).isEqualTo("all evening");
    }

    @Test
    void anInternalLinkRendersToThePathItsRouteServes() {
        FormatterContext context = attach("website", LinkFieldType.ID, Map.of(), Map.of());

        Document document = Jsoup.parseBodyFragment(formatters.render(context, Map.of(
                LinkConstraint.URI_KEY, "internal:/about",
                LinkConstraint.TITLE_KEY, "About us")));

        assertThat(document.selectFirst("a").attr("href")).isEqualTo("/about");
        assertThat(document.selectFirst("a").text()).isEqualTo("About us");
        assertThat(document.selectFirst("a").hasAttr("target")).isFalse();
        assertThat(formatters.formatter(context).id()).isEqualTo(LinkFormatter.ID);
    }

    @Test
    void anExternalLinkOpensAwayFromThePageAndCannotReachBack() {
        FormatterContext context = attach("website", LinkFieldType.ID, Map.of(), Map.of());

        Document document = Jsoup.parseBodyFragment(formatters.render(context,
                Map.of(LinkConstraint.URI_KEY, "https://example.com/about")));

        assertThat(document.selectFirst("a").attr("target")).isEqualTo("_blank");
        assertThat(document.selectFirst("a").attr("rel")).isEqualTo("noopener noreferrer");
    }

    @Test
    void aLinkWithNoTitleIsShownByWhereItGoes() {
        FormatterContext context = attach("website", LinkFieldType.ID, Map.of(), Map.of());

        assertThat(Jsoup.parseBodyFragment(formatters.render(context,
                        Map.of(LinkConstraint.URI_KEY, "https://example.com/about",
                                LinkConstraint.TITLE_KEY, "  ")))
                .selectFirst("a").text()).isEqualTo("https://example.com/about");
    }

    @Test
    void aLongLinkIsTrimmedToTheLengthTheDisplayAsksFor() {
        FormatterContext context = attach("website", LinkFieldType.ID, Map.of(),
                Map.of(LinkFormatter.TRIM_LENGTH, 12));

        assertThat(Jsoup.parseBodyFragment(formatters.render(context,
                        Map.of(LinkConstraint.URI_KEY, "https://example.com/a/very/long/path")))
                .selectFirst("a").text()).isEqualTo("https://exam...");
    }

    @Test
    void aShortLinkIsLeftWhole() {
        FormatterContext context = attach("website", LinkFieldType.ID, Map.of(),
                Map.of(LinkFormatter.TRIM_LENGTH, 50));

        assertThat(Jsoup.parseBodyFragment(formatters.render(context,
                        Map.of(LinkConstraint.URI_KEY, "https://example.com")))
                .selectFirst("a").text()).isEqualTo("https://example.com");
    }

    @Test
    void aValueThatIsNotALinkReadsAsItStands() {
        FormatterContext context = attach("website", LinkFieldType.ID, Map.of(), Map.of());

        assertThat(text(context, "https://example.com")).isEqualTo("https://example.com");
    }

    @TestConfiguration
    static class NoticeTypes {

        @Bean
        EntityTypeProvider noticeTypes() {
            return () -> List.of(NOTICE, NOTICE_TYPE);
        }

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
