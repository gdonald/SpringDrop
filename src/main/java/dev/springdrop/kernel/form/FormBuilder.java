package dev.springdrop.kernel.form;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Runs a form through its lifecycle. Building publishes a {@link FormAlterEvent}
 * so modules can change the tree, then renders it. Handling a submission fills
 * the state from what was posted, validates required elements and then the
 * form's own rules, and either renders the form again with its errors, rebuilds
 * it for the next step, or submits it.
 */
@Component
public class FormBuilder {

    private final Map<String, Form> forms;
    private final FormRenderer renderer;
    private final ApplicationEventPublisher events;

    public FormBuilder(List<Form> forms, FormRenderer renderer, ApplicationEventPublisher events) {
        this.forms = forms.stream().collect(Collectors.toMap(form -> form.id(), Function.identity()));
        this.renderer = renderer;
        this.events = events;
    }

    public Form form(String formId) {
        Form form = forms.get(formId);
        if (form == null) {
            throw new IllegalArgumentException("No form registered with id '" + formId + "'");
        }
        return form;
    }

    /** The form as it is first shown, with no submission behind it. */
    public String render(String formId) {
        return render(formId, new FormState());
    }

    public String render(String formId, FormState state) {
        return renderer.render(buildTree(formId, state), state.errors());
    }

    /**
     * Validates and submits what was posted. A form with errors is rendered
     * again with them, a form asking to rebuild is rendered again without
     * submitting, and anything else is submitted.
     */
    public FormOutcome handle(String formId, Map<String, Object> submitted) {
        Form form = form(formId);
        FormState state = FormState.of(submitted);
        FormElement tree = buildTree(formId, state);

        validateRequired(tree, state);
        if (!state.hasErrors()) {
            form.validate(state);
        }

        if (state.hasErrors()) {
            return new FormOutcome(false, renderer.render(tree, state.errors()), state);
        }
        if (state.rebuild()) {
            return new FormOutcome(false, render(formId, state), state);
        }

        form.submit(state);
        return new FormOutcome(true, "", state);
    }

    private FormElement buildTree(String formId, FormState state) {
        FormElement tree = form(formId).build(state);
        events.publishEvent(new FormAlterEvent(formId, tree, state));
        return tree;
    }

    /**
     * Every element the person can see and fill in must carry a value when it is
     * required. An element hidden by its conditions is not enforced, since the
     * person was never shown it, and one whose conditions make it required is,
     * even though the element itself is not marked required.
     */
    private static void validateRequired(FormElement element, FormState state) {
        if (!element.access() || !isVisible(element, state)) {
            return;
        }
        Object value = state.values().get(element.name());
        if (isRequired(element, state) && isEmpty(value)) {
            state.error(element.name(), ValidationRule.REQUIRED_MESSAGE);
        } else {
            checkRules(element, value, state);
        }
        element.children().forEach(child -> validateRequired(child, state));
    }

    /** The element's own rules, the same ones the browser was handed. */
    private static void checkRules(FormElement element, Object value, FormState state) {
        for (ValidationRule rule : element.rules()) {
            Optional<String> violation = rule.check(value == null ? null : value.toString());
            if (violation.isPresent()) {
                state.error(element.name(), violation.get());
                return;
            }
        }
    }

    private static boolean isVisible(FormElement element, FormState state) {
        return statesOf(element, ElementState.VISIBLE).allMatch(condition -> holds(condition, state));
    }

    private static boolean isRequired(FormElement element, FormState state) {
        return element.required()
                || statesOf(element, ElementState.REQUIRED).anyMatch(condition -> holds(condition, state));
    }

    private static Stream<ElementState> statesOf(FormElement element, String condition) {
        return element.states().stream().filter(state -> state.condition().equals(condition));
    }

    private static boolean holds(ElementState condition, FormState state) {
        return condition.holds(state.values().get(condition.dependsOn()));
    }

    private static boolean isEmpty(Object value) {
        return value == null || value.toString().isBlank();
    }
}
