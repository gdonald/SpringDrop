package dev.springdrop.kernel.form;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

/**
 * The rules a form declares are checked here and, with the same wording, in
 * {@code src/test/frontend/form-validation.test.js}. The renderer hands the
 * browser the message this side produces, so neither side carries wording of its
 * own.
 */
class FormValidationRuleTest {

    private static final String TOO_LONG = "This value is too long. It must be at most 10 characters.";

    private static final String NOT_EMAIL = "This value is not an email address.";

    private static final String REQUIRED = "This value is required.";

    private static final String OUT_OF_RANGE = "This value should be between 1 and 10.";

    private static final String WRONG_FORMAT = "This value is not in the expected format.";

    private final FormRenderer renderer = new FormRenderer();

    private Element render(FormElement element) {
        return Jsoup.parseBodyFragment(renderer.render(element)).selectFirst("input");
    }

    private static FormElement field(ValidationRule rule) {
        return FormElement.of(ElementType.TEXTFIELD, "value").label("Value").rule(rule);
    }

    @Test
    void aMaximumLengthRuleAcceptsAValueWithinIt() {
        assertThat(ValidationRule.maxLength(10).check("short")).isEmpty();
    }

    @Test
    void aMaximumLengthRuleReportsTheSameMessageItHandsTheBrowser() {
        ValidationRule rule = ValidationRule.maxLength(10);

        assertThat(rule.check("far too long to fit")).contains(TOO_LONG);
        assertThat(render(field(rule)).attr("data-rule-maxlength")).isEqualTo("10");
        assertThat(render(field(rule)).attr("data-rule-maxlength-message")).isEqualTo(TOO_LONG);
    }

    @Test
    void anEmailRuleReportsTheSameMessageItHandsTheBrowser() {
        ValidationRule rule = ValidationRule.email();

        assertThat(rule.check("alice at example")).contains(NOT_EMAIL);
        assertThat(rule.check("alice@example.com")).isEmpty();
        assertThat(render(field(rule)).attr("data-rule-email-message")).isEqualTo(NOT_EMAIL);
    }

    @Test
    void aPatternRuleReportsTheSameMessageItHandsTheBrowser() {
        ValidationRule rule = ValidationRule.pattern("[a-z_]+");

        assertThat(rule.check("Not This")).contains(WRONG_FORMAT);
        assertThat(rule.check("machine_name")).isEmpty();
        assertThat(render(field(rule)).attr("data-rule-pattern")).isEqualTo("[a-z_]+");
        assertThat(render(field(rule)).attr("data-rule-pattern-message")).isEqualTo(WRONG_FORMAT);
    }

    @Test
    void aRangeRuleReportsTheSameMessageItHandsTheBrowser() {
        ValidationRule rule = ValidationRule.range("1", "10");

        assertThat(rule.check("0")).contains(OUT_OF_RANGE);
        assertThat(rule.check("11")).contains(OUT_OF_RANGE);
        assertThat(rule.check("5")).isEmpty();
        assertThat(render(field(rule)).attr("data-rule-range")).isEqualTo("1:10");
        assertThat(render(field(rule)).attr("data-rule-range-message")).isEqualTo(OUT_OF_RANGE);
    }

    @Test
    void anOpenEndedRangeBoundsOnlyTheSideItNames() {
        assertThat(ValidationRule.range("1", "").check("9000")).isEmpty();
        assertThat(ValidationRule.range("1", "").check("0")).contains("This value should be 1 or more.");
        assertThat(ValidationRule.range("", "10").check("-5")).isEmpty();
        assertThat(ValidationRule.range("", "10").check("11")).contains("This value should be 10 or less.");
    }

    @Test
    void aRequiredElementHandsTheBrowserTheRuleTheServerEnforces() {
        Element input = render(FormElement.of(ElementType.TEXTFIELD, "value").label("Value").markRequired());

        assertThat(input.hasAttr("data-rule-required")).isTrue();
        assertThat(input.attr("data-rule-required-message")).isEqualTo(REQUIRED);
    }

    @Test
    void anEmptyValueIsLeftToTheRequiredRule() {
        assertThat(ValidationRule.maxLength(10).check("")).isEmpty();
        assertThat(ValidationRule.maxLength(10).check(null)).isEmpty();
    }

    @Test
    void aRuleCanCarryWordingOfItsOwn() {
        ValidationRule rule = ValidationRule.maxLength(10).withMessage("Keep it brief.");

        assertThat(rule.check("far too long to fit")).contains("Keep it brief.");
        assertThat(render(field(rule)).attr("data-rule-maxlength-message")).isEqualTo("Keep it brief.");
    }

    @Test
    void aRuleOfAnUnknownKindPassesRatherThanBlocking() {
        ValidationRule rule = new ValidationRule("no_such_rule", "", "Never shown.");

        assertThat(rule.check("anything")).isEmpty();
    }

    @Test
    void everyRuleAnElementCarriesReachesTheBrowser() {
        Element input = render(FormElement.of(ElementType.TEXTFIELD, "value")
                .label("Value")
                .markRequired()
                .rule(ValidationRule.maxLength(10))
                .rule(ValidationRule.email()));

        assertThat(input.attributes().asList())
                .extracting(attribute -> attribute.getKey())
                .contains("data-rule-required", "data-rule-maxlength", "data-rule-email");
    }

    @Test
    void theServerReportsTheFirstRuleAValueBreaks() {
        FormElement element = FormElement.of(ElementType.TEXTFIELD, "value")
                .rule(ValidationRule.maxLength(10))
                .rule(ValidationRule.email());
        FormState state = FormState.of(Map.of("value", "far too long to fit"));

        assertThat(new FormRenderer().render(element)).contains("data-rule-maxlength");
        assertThat(element.rules().getFirst().check(state.value("value").orElseThrow().toString()))
                .contains(TOO_LONG);
    }
}
