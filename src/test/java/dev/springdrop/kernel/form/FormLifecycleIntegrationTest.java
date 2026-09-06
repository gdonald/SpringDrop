package dev.springdrop.kernel.form;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.springdrop.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;

@SpringBootTest
class FormLifecycleIntegrationTest extends AbstractIntegrationTest {

    static final String SUBSCRIBE_FORM = "subscribe";

    static final String SURVEY_FORM = "survey";

    static final String PING_FORM = "ping";

    @Autowired
    private FormBuilder forms;

    @Autowired
    private SubscribeForm subscribeForm;

    @Autowired
    private SurveyForm surveyForm;

    @Autowired
    private PingForm pingForm;

    @BeforeEach
    void nothingSubmittedYet() {
        subscribeForm.submissions.clear();
        surveyForm.submissions.clear();
        pingForm.submissions.clear();
    }

    private Document parse(String markup) {
        return Jsoup.parseBodyFragment(markup);
    }

    @Test
    void aFormRendersItsElementsBeforeAnythingIsSubmitted() {
        Document document = parse(forms.render(SUBSCRIBE_FORM));

        assertThat(document.selectFirst("input#mail")).isNotNull();
        assertThat(document.selectFirst("button[type=submit]").text()).isEqualTo("Subscribe");
    }

    @Test
    void aValidSubmissionReachesTheFormAndRendersNothingBack() {
        FormOutcome outcome = forms.handle(SUBSCRIBE_FORM, Map.of("mail", "alice@example.com"));

        assertThat(outcome.submitted()).isTrue();
        assertThat(outcome.markup()).isEmpty();
        assertThat(subscribeForm.submissions).containsExactly("alice@example.com");
    }

    @Test
    void aMissingRequiredValueIsReportedOnItsOwnFieldAndNothingIsSubmitted() {
        FormOutcome outcome = forms.handle(SUBSCRIBE_FORM, Map.of("mail", ""));

        assertThat(outcome.submitted()).isFalse();
        assertThat(outcome.state().errors()).containsEntry("mail", "This value is required.");
        assertThat(parse(outcome.markup()).selectFirst("input#mail").hasClass("is-invalid")).isTrue();
        assertThat(subscribeForm.submissions).isEmpty();
    }

    @Test
    void aValueTheFormRejectsIsReportedWithoutSubmitting() {
        FormOutcome outcome = forms.handle(SUBSCRIBE_FORM, Map.of("mail", "alice at example"));

        assertThat(outcome.submitted()).isFalse();
        assertThat(outcome.state().errors()).containsEntry("mail", "Enter an email address.");
        assertThat(parse(outcome.markup()).selectFirst("div.invalid-feedback").text())
                .isEqualTo("Enter an email address.");
        assertThat(subscribeForm.submissions).isEmpty();
    }

    @Test
    void theFormsOwnChecksWaitUntilTheRequiredValuesAreThere() {
        FormOutcome outcome = forms.handle(SUBSCRIBE_FORM, Map.of("mail", ""));

        assertThat(outcome.state().errors()).containsOnlyKeys("mail");
        assertThat(outcome.state().errors().get("mail")).isEqualTo("This value is required.");
    }

    @Test
    void aRequiredValueLeftOutOfTheSubmissionIsReported() {
        FormOutcome outcome = forms.handle(SUBSCRIBE_FORM, Map.of());

        assertThat(outcome.submitted()).isFalse();
        assertThat(outcome.state().errors()).containsEntry("mail", "This value is required.");
    }

    @Test
    void aFormWithNoChecksOfItsOwnSubmitsWhatItIsGiven() {
        FormOutcome outcome = forms.handle(PING_FORM, Map.of("note", "still here"));

        assertThat(outcome.submitted()).isTrue();
        assertThat(pingForm.submissions).containsExactly("still here");
    }

    @Test
    void aRuleTheValueBreaksIsReportedWithTheRulesOwnMessage() {
        FormOutcome outcome = forms.handle(PING_FORM, Map.of("note", "far too long to fit"));

        assertThat(outcome.submitted()).isFalse();
        assertThat(outcome.state().errors())
                .containsEntry("note", "This value is too long. It must be at most 10 characters.");
        assertThat(pingForm.submissions).isEmpty();
    }

    @Test
    void aRuleOnAValueThatWasNotSubmittedPassesQuietly() {
        FormOutcome outcome = forms.handle(PING_FORM, Map.of());

        assertThat(outcome.submitted()).isTrue();
        assertThat(pingForm.submissions).containsExactly("");
    }

    @Test
    void aValueSetOnTheStateIsCarriedWithIt() {
        FormState state = new FormState().value("mail", "alice@example.com");

        assertThat(state.values()).containsEntry("mail", "alice@example.com");
    }

    @Test
    void aRequiredElementTheUserCannotSeeIsNotEnforced() {
        FormOutcome outcome = forms.handle(SUBSCRIBE_FORM,
                Map.of("mail", "alice@example.com", "referrer", ""));

        assertThat(outcome.submitted()).isTrue();
    }

    @Test
    void aFirstStepAdvancesWithoutSubmittingAndKeepsWhatWasEntered() {
        FormOutcome outcome = forms.handle(SURVEY_FORM, Map.of("name", "Alice", "step", "1"));

        assertThat(outcome.submitted()).isFalse();
        assertThat(outcome.state().storage()).containsEntry("name", "Alice");
        assertThat(parse(outcome.markup()).selectFirst("input#comment")).isNotNull();
        assertThat(surveyForm.submissions).isEmpty();
    }

    @Test
    void theLastStepSubmitsTheWholeAnswer() {
        FormOutcome outcome = forms.handle(SURVEY_FORM,
                Map.of("name", "Alice", "comment", "It went well", "step", "2"));

        assertThat(outcome.submitted()).isTrue();
        assertThat(surveyForm.submissions).containsExactly("Alice: It went well");
    }

    @Test
    void aListenerCanAddAnElementBeforeTheFormIsRendered() {
        assertThat(parse(forms.render(SUBSCRIBE_FORM)).selectFirst("input#source")).isNotNull();
    }

    @Test
    void theTriggeringElementIsTheOneTheStateWasToldAbout() {
        FormState state = FormState.of(Map.of("mail", "alice@example.com")).triggeredBy("save");

        assertThat(state.triggeringElement()).isEqualTo("save");
        assertThat(state.value("mail")).contains("alice@example.com");
        assertThat(state.value("missing")).isEmpty();
    }

    @Test
    void anUnknownFormIsRejected() {
        assertThatThrownBy(() -> forms.render("nonesuch"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonesuch");
    }

    @TestConfiguration
    static class Forms {

        @Bean
        SubscribeForm subscribeForm() {
            return new SubscribeForm();
        }

        @Bean
        SurveyForm surveyForm() {
            return new SurveyForm();
        }

        @Bean
        PingForm pingForm() {
            return new PingForm();
        }

        @Bean
        SourceFieldAdder sourceFieldAdder() {
            return new SourceFieldAdder();
        }
    }

    /** A one-step form with a required field and a rule of its own. */
    static class SubscribeForm implements Form {

        final List<String> submissions = new ArrayList<>();

        @Override
        public String id() {
            return SUBSCRIBE_FORM;
        }

        @Override
        public FormElement build(FormState state) {
            return FormElement.of(ElementType.CONTAINER, "subscribe")
                    .child(FormElement.of(ElementType.TEXTFIELD, "mail").label("Email").markRequired())
                    .child(FormElement.of(ElementType.TEXTFIELD, "referrer")
                            .label("Referrer").markRequired().withoutAccess())
                    .child(FormElement.of(ElementType.ACTIONS, "actions")
                            .child(FormElement.of(ElementType.SUBMIT, "save").label("Subscribe")));
        }

        @Override
        public void validate(FormState state) {
            state.value("mail")
                    .filter(mail -> !mail.toString().contains("@"))
                    .ifPresent(mail -> state.error("mail", "Enter an email address."));
        }

        @Override
        public void submit(FormState state) {
            submissions.add(state.value("mail").orElseThrow().toString());
        }
    }

    /** A two-step form that rebuilds after the first step. */
    static class SurveyForm implements Form {

        final List<String> submissions = new ArrayList<>();

        @Override
        public String id() {
            return SURVEY_FORM;
        }

        @Override
        public FormElement build(FormState state) {
            boolean secondStep = "1".equals(state.value("step").map(value -> value.toString()).orElse(""));
            FormElement form = FormElement.of(ElementType.CONTAINER, "survey");
            if (secondStep) {
                form.child(FormElement.of(ElementType.TEXTFIELD, "comment").label("Comment"));
            } else {
                form.child(FormElement.of(ElementType.TEXTFIELD, "name").label("Name"));
            }
            return form.child(FormElement.of(ElementType.SUBMIT, "next").label("Next"));
        }

        @Override
        public void validate(FormState state) {
            if ("1".equals(state.value("step").map(value -> value.toString()).orElse(""))) {
                state.store("name", state.value("name").orElseThrow());
                state.rebuild(true);
            }
        }

        @Override
        public void submit(FormState state) {
            submissions.add(state.value("name").orElseThrow() + ": " + state.value("comment").orElseThrow());
        }
    }

    /** A form with nothing to check beyond what the elements say. */
    static class PingForm implements Form {

        final List<String> submissions = new ArrayList<>();

        @Override
        public String id() {
            return PING_FORM;
        }

        @Override
        public FormElement build(FormState state) {
            return FormElement.of(ElementType.CONTAINER, "ping")
                    .child(FormElement.of(ElementType.TEXTFIELD, "note").label("Note")
                            .rule(ValidationRule.maxLength(10)));
        }

        @Override
        public void submit(FormState state) {
            submissions.add(state.value("note").map(value -> value.toString()).orElse(""));
        }
    }

    /** Stands in for a module adding a field to someone else's form. */
    static class SourceFieldAdder {

        @EventListener
        void addSource(FormAlterEvent event) {
            if (SUBSCRIBE_FORM.equals(event.formId()) && !event.state().hasErrors()) {
                event.subject().child(FormElement.of(ElementType.TEXTFIELD, "source").label("How did you hear?"));
            }
        }
    }
}
