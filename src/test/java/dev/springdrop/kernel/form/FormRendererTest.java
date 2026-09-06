package dev.springdrop.kernel.form;

import static org.assertj.core.api.Assertions.assertThat;

import dev.springdrop.support.BootstrapAssertions;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

class FormRendererTest {

    private final FormRenderer renderer = new FormRenderer();

    private Document render(FormElement element) {
        return Jsoup.parseBodyFragment(renderer.render(element));
    }

    private Document render(FormElement element, Map<String, String> errors) {
        return Jsoup.parseBodyFragment(renderer.render(element, errors));
    }

    private static FormElement element(ElementType type, String name) {
        return FormElement.of(type, name);
    }

    @Test
    void aTextfieldRendersAsABootstrapControlWithItsLabel() {
        Document document = render(element(ElementType.TEXTFIELD, "title")
                .label("Title")
                .value("Hello"));

        Element input = document.selectFirst("input[type=text]");
        assertThat(input.hasClass("form-control")).isTrue();
        assertThat(input.attr("name")).isEqualTo("title");
        assertThat(input.attr("value")).isEqualTo("Hello");
        assertThat(document.selectFirst("label.form-label").text()).startsWith("Title");
    }

    @Test
    void aRequiredFieldIsMarkedForBothThePersonAndTheBrowser() {
        Document document = render(element(ElementType.TEXTFIELD, "title").label("Title").markRequired());

        assertThat(document.selectFirst("input").hasAttr("required")).isTrue();
        assertThat(document.selectFirst("label").text()).contains("*");
    }

    @Test
    void helpTextRendersUnderTheControl() {
        Document document = render(element(ElementType.TEXTFIELD, "title")
                .label("Title")
                .description("Keep it short"));

        assertThat(document.selectFirst("div.form-text").text()).isEqualTo("Keep it short");
    }

    @Test
    void anErrorRendersAsInvalidFeedbackOnItsOwnField() {
        FormElement form = element(ElementType.CONTAINER, "form")
                .child(element(ElementType.TEXTFIELD, "title").label("Title"))
                .child(element(ElementType.TEXTFIELD, "summary").label("Summary"));

        Document document = render(form, Map.of("title", "This value is required."));

        assertThat(document.selectFirst("input#title").hasClass("is-invalid")).isTrue();
        assertThat(document.selectFirst("input#summary").hasClass("is-invalid")).isFalse();
        assertThat(document.select("div.invalid-feedback")).singleElement()
                .satisfies(feedback -> assertThat(feedback.text()).isEqualTo("This value is required."));
    }

    @Test
    void aTextareaCarriesItsValueAsItsContent() {
        Document document = render(element(ElementType.TEXTAREA, "body").label("Body").value("Line one"));

        Element textarea = document.selectFirst("textarea");
        assertThat(textarea.hasClass("form-control")).isTrue();
        assertThat(textarea.text()).isEqualTo("Line one");
    }

    @Test
    void aTextareaShowsAnErrorOnItself() {
        Document document = render(
                element(ElementType.TEXTAREA, "body").label("Body"), Map.of("body", "Too long."));

        assertThat(document.selectFirst("textarea").hasClass("is-invalid")).isTrue();
    }

    @Test
    void aSelectRendersItsOptionsAndMarksTheChosenOne() {
        Document document = render(element(ElementType.SELECT, "status")
                .label("Status")
                .options(List.of(new SelectOption("draft", "Draft"), new SelectOption("live", "Published")))
                .value("live"));

        Element select = document.selectFirst("select");
        assertThat(select.hasClass("form-select")).isTrue();
        assertThat(select.select("option")).hasSize(2);
        assertThat(select.selectFirst("option[selected]").attr("value")).isEqualTo("live");
    }

    @Test
    void aSelectShowsAnErrorOnItself() {
        Document document = render(element(ElementType.SELECT, "status").label("Status")
                .options(List.of(new SelectOption("draft", "Draft"))), Map.of("status", "Pick one."));

        assertThat(document.selectFirst("select").hasClass("is-invalid")).isTrue();
    }

    @Test
    void radiosRenderOneCheckPerOptionWithTheChosenOneChecked() {
        Document document = render(element(ElementType.RADIOS, "rating")
                .label("Rating")
                .options(List.of(new SelectOption("1", "Poor"), new SelectOption("5", "Excellent")))
                .value("5"));

        assertThat(document.select("div.form-check")).hasSize(2);
        assertThat(document.selectFirst("input[checked]").attr("value")).isEqualTo("5");
        assertThat(document.selectFirst("input").hasClass("form-check-input")).isTrue();
    }

    @Test
    void checkboxesRenderAsCheckInputs() {
        Document document = render(element(ElementType.CHECKBOXES, "tags")
                .label("Tags")
                .options(List.of(new SelectOption("news", "News"))));

        assertThat(document.selectFirst("input[type=checkbox]").hasClass("form-check-input")).isTrue();
    }

    @Test
    void choicesShowAnErrorOnEachInput() {
        Document document = render(element(ElementType.RADIOS, "rating")
                .options(List.of(new SelectOption("1", "Poor"))), Map.of("rating", "Pick one."));

        assertThat(document.selectFirst("input").hasClass("is-invalid")).isTrue();
    }

    @Test
    void aSingleCheckboxCarriesItsLabelBesideTheBox() {
        Document document = render(element(ElementType.CHECKBOX, "published")
                .label("Published")
                .value(true));

        assertThat(document.selectFirst("input[type=checkbox]").hasAttr("checked")).isTrue();
        assertThat(document.selectFirst("label.form-check-label").text()).isEqualTo("Published");
        assertThat(document.select("label.form-label")).isEmpty();
    }

    @Test
    void anUncheckedCheckboxShowsAnErrorOnItself() {
        Document document = render(element(ElementType.CHECKBOX, "agreed").label("I agree"),
                Map.of("agreed", "You must agree."));

        Element input = document.selectFirst("input[type=checkbox]");
        assertThat(input.hasAttr("checked")).isFalse();
        assertThat(input.hasClass("is-invalid")).isTrue();
    }

    @Test
    void aNumberFieldRendersItsBoundsFromItsAttributes() {
        Document document = render(element(ElementType.NUMBER, "count")
                .label("Count")
                .attribute("min", "1")
                .attribute("max", "10")
                .attribute("step", "1"));

        Element input = document.selectFirst("input[type=number]");
        assertThat(input.attr("min")).isEqualTo("1");
        assertThat(input.attr("max")).isEqualTo("10");
        assertThat(input.attr("step")).isEqualTo("1");
    }

    @Test
    void aDateFieldRendersANativeDateInput() {
        assertThat(render(element(ElementType.DATE, "born_on").label("Born on"))
                .selectFirst("input[type=date]")).isNotNull();
    }

    @Test
    void aFileFieldRendersAFileInput() {
        assertThat(render(element(ElementType.FILE, "photo").label("Photo"))
                .selectFirst("input[type=file]").hasClass("form-control")).isTrue();
    }

    @Test
    void aHiddenFieldRendersWithoutAWrapperOrLabel() {
        Document document = render(element(ElementType.HIDDEN, "token").value("abc123"));

        assertThat(document.selectFirst("input[type=hidden]").attr("value")).isEqualTo("abc123");
        assertThat(document.select("div.mb-3")).isEmpty();
    }

    @Test
    void aValueElementRendersNothingAtAll() {
        assertThat(renderer.render(element(ElementType.VALUE, "internal").value("kept"))).isEmpty();
    }

    @Test
    void aTextElementRendersItsWordsAsAParagraph() {
        Document document = render(element(ElementType.TEXT, "question")
                .label("Delete this?")
                .attribute("class", "h5"));

        assertThat(document.selectFirst("p.h5").text()).isEqualTo("Delete this?");
    }

    @Test
    void aLinkRendersAsAButtonRatherThanBareText() {
        Document document = render(element(ElementType.LINK, "cancel").label("Cancel").value("/articles"));

        Element link = document.selectFirst("a");
        assertThat(link.hasClass("btn")).isTrue();
        assertThat(link.attr("href")).isEqualTo("/articles");
        BootstrapAssertions.assertNoOutlineButtons(document);
    }

    @Test
    void aSubmitButtonIsSolidRatherThanOutlineStyle() {
        Document document = render(element(ElementType.SUBMIT, "save").label("Save"));

        Element button = document.selectFirst("button[type=submit]");
        assertThat(button.hasClass("btn-primary")).isTrue();
        assertThat(button.text()).isEqualTo("Save");
        BootstrapAssertions.assertNoOutlineButtons(document);
    }

    @Test
    void aFieldsetGroupsItsChildrenUnderALegend() {
        Document document = render(element(ElementType.FIELDSET, "meta")
                .label("Metadata")
                .child(element(ElementType.TEXTFIELD, "author").label("Author")));

        assertThat(document.selectFirst("fieldset legend").text()).isEqualTo("Metadata");
        assertThat(document.selectFirst("fieldset input#author")).isNotNull();
    }

    @Test
    void detailsGroupItsChildrenUnderASummary() {
        Document document = render(element(ElementType.DETAILS, "advanced")
                .label("Advanced")
                .child(element(ElementType.TEXTFIELD, "path").label("Path")));

        assertThat(document.selectFirst("details summary").text()).isEqualTo("Advanced");
        assertThat(document.selectFirst("details input#path")).isNotNull();
    }

    @Test
    void verticalTabsRenderAsAPillNavigation() {
        Document document = render(element(ElementType.VERTICAL_TABS, "tabs")
                .child(element(ElementType.TEXTFIELD, "path").label("Path")));

        Element tabs = document.selectFirst("div.nav-pills");
        assertThat(tabs.hasClass("flex-column")).isTrue();
        assertThat(tabs.selectFirst("input#path")).isNotNull();
    }

    @Test
    void actionsSitTogetherAtTheEndOfTheForm() {
        Document document = render(element(ElementType.ACTIONS, "actions")
                .child(element(ElementType.SUBMIT, "save").label("Save")));

        assertThat(document.selectFirst("div.d-flex").selectFirst("button")).isNotNull();
    }

    @Test
    void aContainerRendersItsChildrenInOrder() {
        Document document = render(element(ElementType.CONTAINER, "form")
                .child(element(ElementType.TEXTFIELD, "first").label("First"))
                .child(element(ElementType.TEXTFIELD, "second").label("Second")));

        assertThat(document.select("input")).extracting(input -> input.attr("name"))
                .containsExactly("first", "second");
    }

    @Test
    void anElementTheUserMayNotSeeIsLeftOut() {
        Document document = render(element(ElementType.CONTAINER, "form")
                .child(element(ElementType.TEXTFIELD, "secret").label("Secret").withoutAccess())
                .child(element(ElementType.TEXTFIELD, "public").label("Public")));

        assertThat(document.select("input")).extracting(input -> input.attr("name"))
                .containsExactly("public");
    }

    @Test
    void everyConditionRendersAsADataAttributeForTheScriptToActOn() {
        Document document = render(element(ElementType.TEXTFIELD, "other")
                .label("Other")
                .visibleWhen("kind", "other")
                .requiredWhen("kind", "other")
                .disabledWhen("locked", "true"));

        Element input = document.selectFirst("input");
        assertThat(input.attr("data-state-visible")).isEqualTo("kind:other");
        assertThat(input.attr("data-state-required")).isEqualTo("kind:other");
        assertThat(input.attr("data-state-disabled")).isEqualTo("locked:true");
    }

    @Test
    void markupFromValuesAndLabelsIsEscaped() {
        Document document = render(element(ElementType.TEXTFIELD, "title")
                .label("<b>Title</b>")
                .value("<script>alert(1)</script>"));

        assertThat(document.select("script")).isEmpty();
        assertThat(document.selectFirst("label").text()).isEqualTo("<b>Title</b>");
        assertThat(document.selectFirst("input").attr("value")).isEqualTo("<script>alert(1)</script>");
    }

    @Test
    void anUnlabelledFieldRendersWithoutALabel() {
        Document document = render(element(ElementType.TEXTFIELD, "search"));

        assertThat(document.select("label")).isEmpty();
        assertThat(document.selectFirst("input")).isNotNull();
    }

    @Test
    void aFormOfEveryElementTypeRendersWithoutOutlineButtons() {
        FormElement form = element(ElementType.CONTAINER, "everything");
        for (ElementType type : ElementType.values()) {
            form.child(element(type, type.name().toLowerCase(java.util.Locale.ROOT)).label(type.name()));
        }

        BootstrapAssertions.assertNoOutlineButtons(render(form));
    }
}
