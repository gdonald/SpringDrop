package dev.springdrop.kernel.field.formatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.springdrop.kernel.entity.BundleDefinition;
import dev.springdrop.kernel.entity.BundleManager;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.field.AllowedValue;
import dev.springdrop.kernel.field.AllowedValues;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldSettings;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.formatter.types.BasicStringFormatter;
import dev.springdrop.kernel.field.formatter.types.BooleanFormatter;
import dev.springdrop.kernel.field.formatter.types.DecimalNumberFormatter;
import dev.springdrop.kernel.field.formatter.types.ListDefaultFormatter;
import dev.springdrop.kernel.field.formatter.types.NumberFormatter;
import dev.springdrop.kernel.field.formatter.types.StringFormatter;
import dev.springdrop.kernel.field.types.BooleanFieldType;
import dev.springdrop.kernel.field.types.DecimalFieldType;
import dev.springdrop.kernel.field.types.IntegerFieldType;
import dev.springdrop.kernel.field.types.ListIntegerFieldType;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.field.types.StringLongFieldType;
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
class FieldFormatterIntegrationTest extends AbstractIntegrationTest {

    record Listing(long id, String label) {
    }

    record ListingType(String id, String label) {
    }

    static final EntityType LISTING = EntityType.content("listing", Listing.class)
            .withBundles("type", "listing_type");

    static final EntityType LISTING_TYPE = EntityType.config("listing_type", ListingType.class);

    private static final String BUNDLE = "offer";

    @Autowired
    private FieldFormatterManager formatters;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private BundleManager bundleManager;

    @BeforeEach
    void aBundleToAttachFieldsTo() {
        entityTypeManager.installStorage("listing");
        bundleManager.save("listing", new BundleDefinition(BUNDLE, "Offer"));
    }

    @AfterEach
    void removeFieldsAndBundle() {
        fields.fieldNames("listing", BUNDLE).forEach(field -> fields.deleteStorage("listing", field));
        bundleManager.delete("listing", BUNDLE);
    }

    private FormatterContext attach(
            FieldStorageConfig storage, String label, Map<String, Object> formatterSettings) {

        fields.createStorage(storage);
        fields.createInstance(FieldInstanceConfig.of(storage.name(), "listing", BUNDLE, label));
        return formatters.context("listing", BUNDLE, storage.name(), formatterSettings);
    }

    private FormatterContext attachSingle(String field, String fieldTypeId, String label) {
        return attach(FieldStorageConfig.single(field, "listing", fieldTypeId), label, Map.of());
    }

    private Document render(FormatterContext context, Object value) {
        return Jsoup.parseBodyFragment(formatters.render(context, value));
    }

    @Test
    void aFieldRendersItsLabelAndItsValue() {
        FormatterContext context = attachSingle("title", StringFieldType.ID, "Title");

        Document document = render(context, "A quiet cottage");

        assertThat(document.selectFirst(".field-label").text()).isEqualTo("Title");
        assertThat(document.selectFirst(".field-item").text()).isEqualTo("A quiet cottage");
    }

    @Test
    void aFieldHoldingSeveralValuesRendersThemAsAListInOrder() {
        FormatterContext context = attach(
                FieldStorageConfig.multiple("tags", "listing", StringFieldType.ID,
                        FieldStorageConfig.UNLIMITED),
                "Tags", Map.of());

        Document document = render(context, List.of("quiet", "rural"));

        assertThat(document.select(".field-items .field-item")).extracting(element -> element.text())
                .containsExactly("quiet", "rural");
    }

    @Test
    void aFieldWithNoValuesRendersNothingAtAll() {
        FormatterContext context = attachSingle("title", StringFieldType.ID, "Title");

        assertThat(formatters.render(context, null)).isEmpty();
        assertThat(formatters.render(context, List.of())).isEmpty();
    }

    @Test
    void aFieldWithNoLabelRendersOnlyItsValue() {
        fields.createStorage(FieldStorageConfig.single("title", "listing", StringFieldType.ID));
        fields.createInstance(FieldInstanceConfig.of("title", "listing", BUNDLE, ""));
        FormatterContext context = formatters.context("listing", BUNDLE, "title");

        Document document = render(context, "A quiet cottage");

        assertThat(document.select(".field-label")).isEmpty();
        assertThat(document.selectFirst(".field-item").text()).isEqualTo("A quiet cottage");
    }

    @Test
    void storedMarkupIsEscapedRatherThanRendered() {
        FormatterContext context = attachSingle("title", StringFieldType.ID, "Title");

        Document document = render(context, "<script>alert(1)</script>");

        assertThat(document.select("script")).isEmpty();
        assertThat(document.selectFirst(".field-item").text()).isEqualTo("<script>alert(1)</script>");
    }

    @Test
    void longTextKeepsTheLineBreaksItWasTypedWith() {
        FormatterContext context = attachSingle("body", StringLongFieldType.ID, "Body");

        Document document = render(context, "First line\nSecond line");

        assertThat(document.select("br")).hasSize(1);
        assertThat(formatters.formatter(context).id()).isEqualTo(BasicStringFormatter.ID);
    }

    @Test
    void aWholeNumberIsGroupedForReading() {
        FormatterContext context = attachSingle("views", IntegerFieldType.ID, "Views");

        assertThat(render(context, 1234567).selectFirst(".field-item").text()).isEqualTo("1,234,567");
        assertThat(formatters.formatter(context).id()).isEqualTo(NumberFormatter.INTEGER_ID);
    }

    @Test
    void aNumberCarriesThePrefixAndSuffixTheDisplayAsksFor() {
        FormatterContext context = attach(
                FieldStorageConfig.single("price", "listing", DecimalFieldType.ID), "Price",
                Map.of(NumberFormatter.PREFIX, "$", NumberFormatter.SUFFIX, " per night"));

        assertThat(render(context, 1234.5).selectFirst(".field-item").text())
                .isEqualTo("$1,234.50 per night");
    }

    @Test
    void aNumberUsesTheSeparatorsTheDisplayAsksFor() {
        FormatterContext context = attach(
                FieldStorageConfig.single("price", "listing", DecimalFieldType.ID), "Price",
                Map.of(NumberFormatter.THOUSANDS_SEPARATOR, ".", NumberFormatter.DECIMAL_SEPARATOR, ","));

        assertThat(render(context, 1234.5).selectFirst(".field-item").text()).isEqualTo("1.234,50");
    }

    @Test
    void aNumberCanBeShownWithNoThousandsSeparatorAtAll() {
        FormatterContext context = attach(
                FieldStorageConfig.single("views", "listing", IntegerFieldType.ID), "Views",
                Map.of(NumberFormatter.THOUSANDS_SEPARATOR, ""));

        assertThat(render(context, 1234567).selectFirst(".field-item").text()).isEqualTo("1 234 567");
    }

    @Test
    void aDecimalKeepsTwoPlacesUnlessTheDisplaySaysOtherwise() {
        FormatterContext context = attachSingle("price", DecimalFieldType.ID, "Price");

        assertThat(render(context, 19.5).selectFirst(".field-item").text()).isEqualTo("19.50");
        assertThat(formatters.formatter(context).id()).isEqualTo(DecimalNumberFormatter.ID);
    }

    @Test
    void aDecimalRoundsToThePlacesTheDisplayAsksFor() {
        FormatterContext context = attach(
                FieldStorageConfig.single("price", "listing", DecimalFieldType.ID), "Price",
                Map.of(NumberFormatter.DECIMAL_PLACES, 1));

        assertThat(render(context, 19.55).selectFirst(".field-item").text()).isEqualTo("19.6");
    }

    @Test
    void aFlagReadsAsYesOrNo() {
        FormatterContext context = attachSingle("available", BooleanFieldType.ID, "Available");

        assertThat(render(context, true).selectFirst(".field-item").text()).isEqualTo("Yes");
        assertThat(render(context, false).selectFirst(".field-item").text()).isEqualTo("No");
        assertThat(formatters.formatter(context).id()).isEqualTo(BooleanFormatter.ID);
    }

    @Test
    void aFlagReadsAsWhateverWordsTheDisplayPrefers() {
        FormatterContext context = attach(
                FieldStorageConfig.single("available", "listing", BooleanFieldType.ID), "Available",
                Map.of(BooleanFormatter.TRUE_LABEL, "Free", BooleanFormatter.FALSE_LABEL, "Taken"));

        assertThat(render(context, true).selectFirst(".field-item").text()).isEqualTo("Free");
        assertThat(render(context, "false").selectFirst(".field-item").text()).isEqualTo("Taken");
    }

    @Test
    void aFlagStoredAsTextStillReadsAsYes() {
        FormatterContext context = attachSingle("available", BooleanFieldType.ID, "Available");

        assertThat(render(context, "true").selectFirst(".field-item").text()).isEqualTo("Yes");
    }

    @Test
    void anOptionReadsAsItsLabelRatherThanItsStoredValue() {
        FormatterContext context = attach(
                new FieldStorageConfig("rating", "listing", ListIntegerFieldType.ID, 1,
                        Map.of(FieldSettings.ALLOWED_VALUES, AllowedValues.setting(
                                List.of(new AllowedValue(1, "Poor"), new AllowedValue(5, "Excellent"))))),
                "Rating", Map.of());

        assertThat(render(context, 5).selectFirst(".field-item").text()).isEqualTo("Excellent");
        assertThat(formatters.formatter(context).id()).isEqualTo(ListDefaultFormatter.ID);
    }

    @Test
    void anOptionThatIsNoLongerOfferedReadsAsItStands() {
        FormatterContext context = attach(
                new FieldStorageConfig("rating", "listing", ListIntegerFieldType.ID, 1,
                        Map.of(FieldSettings.ALLOWED_VALUES, AllowedValues.setting(
                                List.of(new AllowedValue(1, "Poor"))))),
                "Rating", Map.of());

        assertThat(render(context, 5).selectFirst(".field-item").text()).isEqualTo("5");
    }

    @Test
    void aDisplayCanNameAnotherFormatterThanTheTypesDefault() {
        FormatterContext context = attach(
                FieldStorageConfig.single("body", "listing", StringLongFieldType.ID), "Body",
                Map.of(FieldFormatterManager.FORMATTER_SETTING, StringFormatter.ID));

        assertThat(formatters.formatter(context).id()).isEqualTo(StringFormatter.ID);
        assertThat(render(context, "First line\nSecond line").select("br")).isEmpty();
    }

    @Test
    void aFieldThatIsNotThereHasNothingToRender() {
        assertThatThrownBy(() -> formatters.context("listing", BUNDLE, "nonesuch"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonesuch");
    }

    @Test
    void aFieldThatIsNotOnThisBundleHasNothingToRender() {
        fields.createStorage(FieldStorageConfig.single("elsewhere", "listing", StringFieldType.ID));

        assertThatThrownBy(() -> formatters.context("listing", "other_bundle", "elsewhere"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("other_bundle");
    }

    @TestConfiguration
    static class ListingTypes {

        @Bean
        EntityTypeProvider listingTypes() {
            return () -> List.of(LISTING, LISTING_TYPE);
        }
    }
}
