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
import dev.springdrop.kernel.field.display.FieldDisplaySlot;
import dev.springdrop.kernel.field.display.FormDisplayConfig;
import dev.springdrop.kernel.field.display.FormDisplayManager;
import dev.springdrop.kernel.field.display.ViewDisplayConfig;
import dev.springdrop.kernel.field.display.ViewDisplayManager;
import dev.springdrop.kernel.field.formatter.types.FormattedTextFormatter;
import dev.springdrop.kernel.field.formatter.types.SummaryOrTrimmedFormatter;
import dev.springdrop.kernel.field.formatter.types.TrimmedTextFormatter;
import dev.springdrop.kernel.field.types.FormattedText;
import dev.springdrop.kernel.field.types.TextFieldType;
import dev.springdrop.kernel.field.types.TextLongFieldType;
import dev.springdrop.kernel.field.types.TextWithSummaryFieldType;
import dev.springdrop.kernel.field.widget.FieldWidgetManager;
import dev.springdrop.kernel.field.widget.WidgetContext;
import dev.springdrop.kernel.field.widget.types.FormattedTextareaWidget;
import dev.springdrop.kernel.field.widget.types.FormattedTextareaWithSummaryWidget;
import dev.springdrop.kernel.field.widget.types.FormattedTextfieldWidget;
import dev.springdrop.kernel.filter.TextFormatManager;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.support.AbstractIntegrationTest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
@Import(FormattedTextIntegrationTest.Types.class)
class FormattedTextIntegrationTest extends AbstractIntegrationTest {

    record Page(long id, String label) {
    }

    record PageType(String id, String label) {
    }

    static final EntityType PAGE = EntityType.content("essay", Page.class).withBundles("type", "essay_type");

    static final EntityType PAGE_TYPE = EntityType.config("essay_type", PageType.class);

    private static final String BUNDLE = "column";

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private EntityCrudService entities;

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private BundleManager bundleManager;

    @Autowired
    private ViewDisplayManager viewDisplays;

    @Autowired
    private FormDisplayManager formDisplays;

    @Autowired
    private FieldWidgetManager widgets;

    @Autowired
    private FormRenderer renderer;

    @BeforeEach
    void aBundleWithABodyASubtitleAndNotes() {
        entityTypeManager.installStorage("essay");
        bundleManager.save("essay", new BundleDefinition(BUNDLE, "Column"));
        attach("body", TextWithSummaryFieldType.ID, Map.of());
        attach("subtitle", TextFieldType.ID, Map.of(FormattedText.MAX_LENGTH, 12));
        attach("notes", TextLongFieldType.ID, Map.of());
    }

    @AfterEach
    void removeFieldsAndBundle() {
        SecurityContextHolder.clearContext();
        fields.fieldNames("essay", BUNDLE).forEach(field -> fields.deleteStorage("essay", field));
        bundleManager.delete("essay", BUNDLE);
        viewDisplays.delete("essay", BUNDLE, ViewDisplayConfig.DEFAULT_MODE);
        formDisplays.delete("essay", BUNDLE, FormDisplayConfig.DEFAULT_MODE);
    }

    private void attach(String field, String type, Map<String, Object> storageSettings) {
        fields.createStorage(new FieldStorageConfig(field, "essay", type, 1, storageSettings));
        fields.createInstance(FieldInstanceConfig.of(field, "essay", BUNDLE, field));
    }

    private static Map<String, Object> text(String value, String format) {
        return Map.of(FormattedText.VALUE, value, FormattedText.FORMAT, format);
    }

    private EntityData saved(long id, Map<String, Object> values) {
        return entities.save(EntityData.of("essay", id, BUNDLE, "An essay", values));
    }

    private void signedInHolding(String... permissions) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "writer", null, List.of(permissions).stream().map(SimpleGrantedAuthority::new).toList()));
    }

    @Test
    void aFormattedValuePersistsItsFormatAndRendersThroughItsFilters() {
        saved(1L, Map.of("notes", text("<p>Open <u>daily</u></p>", TextFormatManager.RESTRICTED_HTML)));

        EntityData loaded = entities.load("essay", 1L).orElseThrow();

        assertThat(loaded.fields().get("notes")).isEqualTo(text("<p>Open <u>daily</u></p>",
                TextFormatManager.RESTRICTED_HTML));
        assertThat(viewDisplays.render("essay", BUNDLE, ViewDisplayConfig.DEFAULT_MODE, loaded.fields()))
                .contains("<p>Open daily</p>").doesNotContain("<u>");
    }

    @Nested
    class Constraints {

        @Test
        void aValueInAFormatTheSiteDoesNotHaveIsRefused() {
            assertThatThrownBy(() -> saved(2L, Map.of("notes", text("Hi", "retired"))))
                    .isInstanceOf(EntityValidationException.class);
        }

        @Test
        void aValueInAFormatThePersonSavingMayNotUseIsRefused() {
            signedInHolding();

            assertThatThrownBy(() -> saved(3L, Map.of("notes", text("Hi", TextFormatManager.FULL_HTML))))
                    .isInstanceOf(EntityValidationException.class);
            saved(3L, Map.of("notes", text("Hi", TextFormatManager.PLAIN_TEXT)));
        }

        @Test
        void aSingleLineValueLongerThanItsStorageAllowsIsRefused() {
            assertThatThrownBy(() -> saved(4L, Map.of("subtitle", text("x".repeat(13), TextFormatManager.PLAIN_TEXT))))
                    .isInstanceOf(EntityValidationException.class);
            saved(4L, Map.of("subtitle", text("x".repeat(12), TextFormatManager.PLAIN_TEXT)));
        }

        @Test
        void aRequiredSummaryHasToBeGiven() {
            fields.createInstance(FieldInstanceConfig.of("body", "essay", BUNDLE, "Body")
                    .withSettings(Map.of(FormattedText.REQUIRED_SUMMARY, true)));
            Map<String, Object> withSummary = new LinkedHashMap<>(text("Body", TextFormatManager.PLAIN_TEXT));

            assertThatThrownBy(() -> saved(5L, Map.of("body", withSummary)))
                    .isInstanceOf(EntityValidationException.class);
            withSummary.put(FormattedText.SUMMARY, "Short");
            saved(5L, Map.of("body", withSummary));
        }

        @Test
        void somethingThatIsNotFormattedTextIsRefused() {
            assertThatThrownBy(() -> saved(6L, Map.of("notes", "bare text")))
                    .isInstanceOf(EntityValidationException.class);
        }
    }

    @Nested
    class Widgets {

        private Document drawn(String field, Object value) {
            WidgetContext context = widgets.context("essay", BUNDLE, field);
            return Jsoup.parseBodyFragment(renderer.render(widgets.build(context,
                    (value == null) ? List.of() : List.of(value), 0)));
        }

        @Test
        void switchingToTheFormattedTextareaDrawsTheFormatChoiceAndTheEditorTarget() {
            formDisplays.save(FormDisplayConfig.of("essay", BUNDLE, FormDisplayConfig.DEFAULT_MODE)
                    .with(FieldDisplaySlot.of("subtitle", FormattedTextareaWidget.ID, 0)));

            Document form = Jsoup.parseBodyFragment(renderer.render(formDisplays.buildContainer("essay", BUNDLE,
                    FormDisplayConfig.DEFAULT_MODE, Map.of())));

            Element textarea = form.selectFirst("textarea[name=subtitle:value]");
            assertThat(textarea.hasAttr("data-editor-target")).isTrue();
            assertThat(form.selectFirst("select[name=subtitle:format]").hasAttr("data-editor-format-selector"))
                    .isTrue();
        }

        @Test
        void theFormatChoiceOffersTheFormatsThePersonMayUseAndNamesEachOnesTags() {
            signedInHolding(TextFormatManager.permission(TextFormatManager.BASIC_HTML));

            Document form = drawn("notes", null);

            assertThat(form.select("select[name=notes:format] option").eachAttr("value"))
                    .containsExactly(TextFormatManager.BASIC_HTML, TextFormatManager.PLAIN_TEXT);
            assertThat(form.selectFirst("textarea[name=notes:value]").attr("data-editor-formats"))
                    .contains("\"basic_html\":[\"a\",\"em\"", "\"plain_text\":[]");
        }

        @Test
        void aStoredValueIsShownWithItsFormatChosenOrTheFirstUsableWhenItsIsNot() {
            assertThat(drawn("notes", text("Hello", TextFormatManager.PLAIN_TEXT))
                    .selectFirst("select[name=notes:format] [selected]").val()).isEqualTo(TextFormatManager.PLAIN_TEXT);
            signedInHolding();
            assertThat(drawn("notes", text("Hello", TextFormatManager.FULL_HTML))
                    .selectFirst("select[name=notes:format] [selected]").val()).isEqualTo(TextFormatManager.PLAIN_TEXT);
        }

        @Test
        void theSummaryIsAskedForUnlessTheFieldHidesItAndRequiredWhenTheFieldSays() {
            assertThat(drawn("body", null).select("textarea[name=body:summary]")).hasSize(1);

            fields.createInstance(FieldInstanceConfig.of("body", "essay", BUNDLE, "Body").asRequired()
                    .withSettings(Map.of(FormattedText.REQUIRED_SUMMARY, true)));
            Document required = drawn("body", null);
            assertThat(required.selectFirst("textarea[name=body:summary]").hasAttr("required")).isTrue();
            assertThat(required.selectFirst("textarea[name=body:value]").hasAttr("required")).isTrue();

            fields.createInstance(FieldInstanceConfig.of("body", "essay", BUNDLE, "Body")
                    .withSettings(Map.of(FormattedText.DISPLAY_SUMMARY, false)));
            assertThat(drawn("body", null).select("textarea[name=body:summary]")).isEmpty();
        }

        @Test
        void eachValueOfAMultiValueFieldHasItsOwnGroupWithTheLabelOnlyOnTheFirst() {
            fields.createStorage(new FieldStorageConfig("quotes", "essay", TextLongFieldType.ID,
                    FieldStorageConfig.UNLIMITED, Map.of()));
            fields.createInstance(FieldInstanceConfig.of("quotes", "essay", BUNDLE, "Quotes").asRequired());

            Document form = drawn("quotes", null);
            WidgetContext context = widgets.context("essay", BUNDLE, "quotes");
            Document two = Jsoup.parseBodyFragment(renderer.render(widgets.build(context, List.of(
                    text("One", TextFormatManager.PLAIN_TEXT), text("Two", TextFormatManager.PLAIN_TEXT)), 0)));

            assertThat(form.selectFirst("textarea[name=quotes[0]:value]").hasAttr("required")).isTrue();
            assertThat(two.select("fieldset legend").eachText()).containsExactly("Quotes");
            assertThat(two.selectFirst("textarea[name=quotes[1]:value]").hasAttr("required")).isFalse();
        }

        @Test
        void aSubmissionIsReadBackAsTextWithItsFormatAndSummary() {
            WidgetContext body = widgets.context("essay", BUNDLE, "body");
            WidgetContext subtitle = widgets.context("essay", BUNDLE, "subtitle");

            assertThat(widgets.extract(body, Map.of("body:value", "Long text", "body:format",
                    TextFormatManager.PLAIN_TEXT, "body:summary", "Short")))
                    .containsExactly(Map.of(FormattedText.VALUE, "Long text", FormattedText.FORMAT,
                            TextFormatManager.PLAIN_TEXT, FormattedText.SUMMARY, "Short"));
            assertThat(widgets.extract(subtitle, Map.of("subtitle:value", "Hours")))
                    .containsExactly(Map.of(FormattedText.VALUE, "Hours", FormattedText.FORMAT, ""));
            assertThat(widgets.extract(subtitle, Map.of("subtitle:value", " "))).isEmpty();
            assertThat(widgets.widget(subtitle).id()).isEqualTo(FormattedTextfieldWidget.ID);
            assertThat(widgets.widget(body).id()).isEqualTo(FormattedTextareaWithSummaryWidget.ID);
            assertThat(widgets.widget(widgets.context("essay", BUNDLE, "notes")).id())
                    .isEqualTo(FormattedTextareaWidget.ID);
            assertThat(drawn("subtitle", null).select("input[type=text][name=subtitle:value]")).hasSize(1);
        }
    }

    @Nested
    class Formatters {

        private String rendered(String formatter, Map<String, Object> settings, Map<String, Object> value) {
            Map<String, Object> slotSettings = new LinkedHashMap<>(settings);
            viewDisplays.save(ViewDisplayConfig.of("essay", BUNDLE, ViewDisplayConfig.DEFAULT_MODE)
                    .with(new FieldDisplaySlot("body", formatter, 0, slotSettings)));
            return viewDisplays.render("essay", BUNDLE, ViewDisplayConfig.DEFAULT_MODE, Map.of("body", value));
        }

        @Test
        void theTrimmedFormatterCutsAtTheConfiguredLengthKeepingTagsClosed() {
            String html = rendered(TrimmedTextFormatter.ID, Map.of(TrimmedTextFormatter.TRIM_LENGTH, 14),
                    text("<p>Opening <em>hours change</em> in May.</p>", TextFormatManager.BASIC_HTML));

            assertThat(html).contains("<p>Opening <em>hours...</em></p>").doesNotContain("May");
        }

        @Test
        void theSummaryOrTrimmedFormatterShowsTheSummaryWhenThereIsOne() {
            Map<String, Object> withSummary = new LinkedHashMap<>(text("<p>The long text.</p>",
                    TextFormatManager.BASIC_HTML));
            withSummary.put(FormattedText.SUMMARY, "<p>In <u>brief</u>.</p>");

            assertThat(rendered(SummaryOrTrimmedFormatter.ID, Map.of(), withSummary))
                    .contains("<p>In brief.</p>").doesNotContain("long text");
            assertThat(rendered(SummaryOrTrimmedFormatter.ID, Map.of(),
                    text("<p>The long text.</p>", TextFormatManager.BASIC_HTML))).contains("The long text.");
        }

        @Test
        void theFormattedFormatterAppliesTheFormatsFilters() {
            assertThat(rendered(FormattedTextFormatter.ID, Map.of(),
                    text("Line one\nhttps://example.com", TextFormatManager.PLAIN_TEXT)))
                    .contains("<p>Line one<br />", "<a href=\"https://example.com\">");
        }

        @Test
        void theTextFormattersNameThemselves() {
            assertThat(List.of(new FormattedTextFormatter(null).id(), new TrimmedTextFormatter(null).id(),
                    new SummaryOrTrimmedFormatter(null).id())).containsExactly(FormattedTextFormatter.ID,
                    TrimmedTextFormatter.ID, SummaryOrTrimmedFormatter.ID);
        }
    }

    @Test
    void eachFormattedTextTypeDescribesItsStorage() {
        for (FieldType type : List.of(fields.fieldType(TextFieldType.ID), fields.fieldType(TextLongFieldType.ID),
                fields.fieldType(TextWithSummaryFieldType.ID))) {
            assertThat(type.properties()).extracting(FieldProperty::name).contains(FormattedText.VALUE,
                    FormattedText.FORMAT);
            assertThat(type.defaultFormatter()).isEqualTo(FormattedTextFormatter.ID);
            assertThat(type.id()).startsWith("text");
        }
        assertThat(fields.fieldType(TextFieldType.ID).defaultStorageSettings())
                .containsEntry(FormattedText.MAX_LENGTH, 255);
        assertThat(fields.fieldType(TextWithSummaryFieldType.ID).defaultInstanceSettings())
                .containsEntry(FormattedText.DISPLAY_SUMMARY, true);
        assertThat(fields.fieldType(TextLongFieldType.ID).defaultInstanceSettings()).isEmpty();
        assertThat(fields.fieldType(TextLongFieldType.ID).defaultStorageSettings()).isEmpty();
        assertThat(fields.fieldType(TextWithSummaryFieldType.ID).defaultStorageSettings()).isEmpty();
        assertThat(fields.fieldType(TextFieldType.ID).defaultInstanceSettings()).isEmpty();
    }

    @TestConfiguration
    static class Types {

        @Bean
        EntityTypeProvider essayTypes() {
            return () -> List.of(PAGE, PAGE_TYPE);
        }
    }
}
