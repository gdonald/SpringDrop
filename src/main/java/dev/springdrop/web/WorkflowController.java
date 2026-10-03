package dev.springdrop.web;

import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.text.MachineNameGenerator;
import dev.springdrop.kernel.web.EntityNotFoundException;
import dev.springdrop.kernel.workflow.WorkflowConfig;
import dev.springdrop.kernel.workflow.WorkflowManager;
import dev.springdrop.kernel.workflow.WorkflowState;
import dev.springdrop.kernel.workflow.WorkflowTransition;
import dev.springdrop.kernel.workflow.types.ContentModerationWorkflowType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The site's workflows: adding one of a workflow type, naming it, and adding,
 * changing, and removing its states and transitions. A change that would leave
 * a workflow unusable comes back with what is wrong and is not saved, and a
 * state the workflow's type requires cannot be removed.
 */
@Controller
public class WorkflowController {

    public static final String PATH = "/admin/config/workflow/workflows";

    public static final String LABEL = "label";

    public static final String TYPE = "type";

    public static final String WEIGHT = "weight";

    /** The prefix of each starting-state box on the transition form, followed by the state's id. */
    public static final String FROM_PREFIX = "from_";

    public static final String TO = "to";

    /** Where a problem with a transition's states is shown on its form. */
    public static final String STATES_ELEMENT = "states";

    static final int LABEL_MAX_LENGTH = 255;

    private final WorkflowManager workflows;
    private final MachineNameGenerator machineNames;
    private final FormBuilder forms;
    private final FormRenderer renderer;

    public WorkflowController(
            WorkflowManager workflows, MachineNameGenerator machineNames, FormBuilder forms, FormRenderer renderer) {
        this.workflows = workflows;
        this.machineNames = machineNames;
        this.forms = forms;
        this.renderer = renderer;
    }

    public static String managePath(String id) {
        return PATH + "/manage/" + id;
    }

    @GetMapping(PATH)
    public String list(Model model) {
        Map<String, String> typeLabels = new LinkedHashMap<>();
        workflows.types().forEach(type -> typeLabels.put(type.id(), type.label()));
        model.addAttribute("title", "Workflows");
        model.addAttribute("description", "The states content moves through, and who may move it.");
        model.addAttribute("workflows", workflows.all());
        model.addAttribute("typeLabels", typeLabels);
        model.addAttribute("addPath", PATH + "/add");
        model.addAttribute("managePath", PATH + "/manage/");
        return "admin/workflows";
    }

    @GetMapping(PATH + "/add")
    public String addForm(Model model) {
        return formPage("Add workflow", PATH + "/add", workflowForm("", "", true), Map.of(), model);
    }

    @PostMapping(PATH + "/add")
    public String add(@RequestParam Map<String, String> submitted, Model model) {
        String label = submitted.getOrDefault(LABEL, "");
        String type = submitted.getOrDefault(TYPE, "");
        FormElement tree = workflowForm(label, type, true);
        FormState state = forms.validate(tree, new LinkedHashMap<>(submitted));
        if (!workflows.hasType(type)) {
            state.error(TYPE, "Choose one of the workflow types the site has.");
        }
        if (state.hasErrors()) {
            return formPage("Add workflow", PATH + "/add", tree, state.errors(), model);
        }
        String id = machineNames.generateUnique(label, taken -> workflows.find(taken).isPresent());
        workflows.save(workflows.create(id, label, type));
        return "redirect:" + managePath(id);
    }

    @GetMapping(PATH + "/manage/{id}")
    public String manage(@PathVariable String id, Model model) {
        WorkflowConfig workflow = workflowOf(id);
        return managePage(workflow, workflowForm(workflow.label(), workflow.type(), false), Map.of(), model);
    }

    @PostMapping(PATH + "/manage/{id}")
    public String rename(@PathVariable String id, @RequestParam Map<String, String> submitted, Model model) {
        WorkflowConfig workflow = workflowOf(id);
        FormElement tree = workflowForm(submitted.getOrDefault(LABEL, ""), workflow.type(), false);
        FormState state = forms.validate(tree, new LinkedHashMap<>(submitted));
        if (state.hasErrors()) {
            return managePage(workflow, tree, state.errors(), model);
        }
        workflows.save(workflow.withLabel(submitted.get(LABEL)));
        return "redirect:" + managePath(id);
    }

    @GetMapping(PATH + "/manage/{id}/delete")
    public String confirmDelete(@PathVariable String id, Model model) {
        WorkflowConfig workflow = workflowOf(id);
        return confirmPage("Delete the workflow " + workflow.label() + "?", managePath(id) + "/delete",
                "Delete workflow", PATH, model);
    }

    @PostMapping(PATH + "/manage/{id}/delete")
    public String delete(@PathVariable String id) {
        workflowOf(id);
        workflows.delete(id);
        return "redirect:" + PATH;
    }

    @GetMapping(PATH + "/manage/{id}/state/add")
    public String addStateForm(@PathVariable String id, Model model) {
        WorkflowConfig workflow = workflowOf(id);
        return formPage("Add a state to " + workflow.label(), managePath(id) + "/state/add",
                stateForm(new WorkflowState("", "", workflow.states().size()), managePath(id)), Map.of(), model);
    }

    @PostMapping(PATH + "/manage/{id}/state/add")
    public String addState(@PathVariable String id, @RequestParam Map<String, String> submitted, Model model) {
        WorkflowConfig workflow = workflowOf(id);
        String label = submitted.getOrDefault(LABEL, "");
        String stateId = label.isBlank() ? "" : machineNames.generateUnique(
                label, taken -> workflow.state(taken).isPresent());
        return saveState(workflow, stateId, submitted, "Add a state to " + workflow.label(),
                managePath(id) + "/state/add", model);
    }

    @GetMapping(PATH + "/manage/{id}/state/{state}")
    public String editStateForm(@PathVariable String id, @PathVariable String state, Model model) {
        WorkflowConfig workflow = workflowOf(id);
        WorkflowState existing = stateOf(workflow, state);
        return formPage("Edit " + existing.label(), managePath(id) + "/state/" + state,
                stateForm(existing, managePath(id)), Map.of(), model);
    }

    @PostMapping(PATH + "/manage/{id}/state/{state}")
    public String editState(@PathVariable String id, @PathVariable String state,
            @RequestParam Map<String, String> submitted, Model model) {
        WorkflowConfig workflow = workflowOf(id);
        WorkflowState existing = stateOf(workflow, state);
        return saveState(workflow, existing.id(), submitted, "Edit " + existing.label(),
                managePath(id) + "/state/" + state, model);
    }

    @GetMapping(PATH + "/manage/{id}/state/{state}/delete")
    public String confirmDeleteState(@PathVariable String id, @PathVariable String state, Model model) {
        WorkflowConfig workflow = workflowOf(id);
        WorkflowState existing = removableStateOf(workflow, state);
        return confirmPage("Delete the state " + existing.label() + "?", managePath(id) + "/state/" + state
                + "/delete", "Delete state", managePath(id), model);
    }

    @PostMapping(PATH + "/manage/{id}/state/{state}/delete")
    public String deleteState(@PathVariable String id, @PathVariable String state) {
        WorkflowConfig workflow = workflowOf(id);
        removableStateOf(workflow, state);
        workflows.save(workflow.withoutState(state));
        return "redirect:" + managePath(id);
    }

    @GetMapping(PATH + "/manage/{id}/transition/add")
    public String addTransitionForm(@PathVariable String id, Model model) {
        WorkflowConfig workflow = workflowOf(id);
        WorkflowTransition blank = new WorkflowTransition("", "", List.of(), "", workflow.transitions().size());
        return formPage("Add a transition to " + workflow.label(), managePath(id) + "/transition/add",
                transitionForm(workflow, blank), Map.of(), model);
    }

    @PostMapping(PATH + "/manage/{id}/transition/add")
    public String addTransition(@PathVariable String id, @RequestParam Map<String, String> submitted, Model model) {
        WorkflowConfig workflow = workflowOf(id);
        String label = submitted.getOrDefault(LABEL, "");
        String transitionId = label.isBlank() ? "" : machineNames.generateUnique(
                label, taken -> workflow.transition(taken).isPresent());
        return saveTransition(workflow, transitionId, submitted, "Add a transition to " + workflow.label(),
                managePath(id) + "/transition/add", model);
    }

    @GetMapping(PATH + "/manage/{id}/transition/{transition}")
    public String editTransitionForm(@PathVariable String id, @PathVariable String transition, Model model) {
        WorkflowConfig workflow = workflowOf(id);
        WorkflowTransition existing = transitionOf(workflow, transition);
        return formPage("Edit " + existing.label(), managePath(id) + "/transition/" + transition,
                transitionForm(workflow, existing), Map.of(), model);
    }

    @PostMapping(PATH + "/manage/{id}/transition/{transition}")
    public String editTransition(@PathVariable String id, @PathVariable String transition,
            @RequestParam Map<String, String> submitted, Model model) {
        WorkflowConfig workflow = workflowOf(id);
        WorkflowTransition existing = transitionOf(workflow, transition);
        return saveTransition(workflow, existing.id(), submitted, "Edit " + existing.label(),
                managePath(id) + "/transition/" + transition, model);
    }

    @GetMapping(PATH + "/manage/{id}/transition/{transition}/delete")
    public String confirmDeleteTransition(@PathVariable String id, @PathVariable String transition, Model model) {
        WorkflowConfig workflow = workflowOf(id);
        WorkflowTransition existing = transitionOf(workflow, transition);
        return confirmPage("Delete the transition " + existing.label() + "?", managePath(id) + "/transition/"
                + transition + "/delete", "Delete transition", managePath(id), model);
    }

    @PostMapping(PATH + "/manage/{id}/transition/{transition}/delete")
    public String deleteTransition(@PathVariable String id, @PathVariable String transition) {
        WorkflowConfig workflow = workflowOf(id);
        transitionOf(workflow, transition);
        workflows.save(workflow.withoutTransition(transition));
        return "redirect:" + managePath(id);
    }

    private String saveState(WorkflowConfig workflow, String stateId, Map<String, String> submitted, String title,
            String action, Model model) {
        WorkflowState candidate = new WorkflowState(stateId, submitted.getOrDefault(LABEL, ""),
                weightOf(submitted));
        FormElement tree = stateForm(candidate, managePath(workflow.id()));
        return saved(workflow.withState(candidate), tree, submitted, LABEL, title, action, model);
    }

    private String saveTransition(WorkflowConfig workflow, String transitionId, Map<String, String> submitted,
            String title, String action, Model model) {
        List<String> from = workflow.states().stream()
                .map(WorkflowState::id)
                .filter(state -> submitted.containsKey(FROM_PREFIX + state))
                .toList();
        WorkflowTransition candidate = new WorkflowTransition(transitionId, submitted.getOrDefault(LABEL, ""),
                from, submitted.getOrDefault(TO, ""), weightOf(submitted));
        FormElement tree = transitionForm(workflow, candidate);
        return saved(workflow.withTransition(candidate), tree, submitted, STATES_ELEMENT, title, action, model);
    }

    /**
     * Saves the changed workflow once the form's rules pass and the workflow is
     * still usable. Otherwise the form comes back with what is wrong, each
     * problem with the workflow shown on the given element.
     */
    private String saved(WorkflowConfig changed, FormElement tree, Map<String, String> submitted,
            String problemElement, String title, String action, Model model) {
        FormState state = forms.validate(tree, new LinkedHashMap<>(submitted));
        if (!state.hasErrors()) {
            List<String> problems = workflows.save(changed);
            if (problems.isEmpty()) {
                return "redirect:" + managePath(changed.id());
            }
            state.error(problemElement, String.join(" ", problems));
        }
        return formPage(title, action, tree, state.errors(), model);
    }

    private static int weightOf(Map<String, String> submitted) {
        String weight = submitted.getOrDefault(WEIGHT, "");
        return weight.matches(BlockLayoutController.WHOLE_NUMBER) ? Integer.parseInt(weight) : 0;
    }

    private FormElement workflowForm(String label, String type, boolean adding) {
        FormElement form = FormElement.of(ElementType.CONTAINER, "workflow")
                .child(FormElement.of(ElementType.TEXTFIELD, LABEL)
                        .label("Label")
                        .markRequired()
                        .value(label)
                        .rule(ValidationRule.maxLength(LABEL_MAX_LENGTH)));
        if (adding) {
            form.child(FormElement.of(ElementType.SELECT, TYPE)
                    .label("Workflow type")
                    .markRequired()
                    .value(type)
                    .options(workflows.types().stream()
                            .map(definition -> new SelectOption(definition.id(), definition.label()))
                            .toList()));
        }
        return form.child(FormElement.of(ElementType.ACTIONS, "actions")
                .child(FormElement.of(ElementType.SUBMIT, "save").label("Save workflow"))
                .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH)));
    }

    private static FormElement stateForm(WorkflowState state, String cancelPath) {
        return FormElement.of(ElementType.CONTAINER, "state")
                .child(FormElement.of(ElementType.TEXTFIELD, LABEL)
                        .label("State label")
                        .markRequired()
                        .value(state.label())
                        .rule(ValidationRule.maxLength(LABEL_MAX_LENGTH)))
                .child(weightElement(state.weight()))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save state"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(cancelPath)));
    }

    private static FormElement transitionForm(WorkflowConfig workflow, WorkflowTransition transition) {
        FormElement from = FormElement.of(ElementType.FIELDSET, STATES_ELEMENT).label("From");
        for (WorkflowState state : workflow.states()) {
            from.child(FormElement.of(ElementType.CHECKBOX, FROM_PREFIX + state.id())
                    .label(state.label())
                    .value(transition.from().contains(state.id())));
        }
        return FormElement.of(ElementType.CONTAINER, "transition")
                .child(FormElement.of(ElementType.TEXTFIELD, LABEL)
                        .label("Transition label")
                        .markRequired()
                        .value(transition.label())
                        .rule(ValidationRule.maxLength(LABEL_MAX_LENGTH)))
                .child(from)
                .child(FormElement.of(ElementType.SELECT, TO)
                        .label("To")
                        .markRequired()
                        .value(transition.to())
                        .options(workflow.states().stream()
                                .map(state -> new SelectOption(state.id(), state.label()))
                                .toList()))
                .child(weightElement(transition.weight()))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save transition"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel")
                                .value(managePath(workflow.id()))));
    }

    private static FormElement weightElement(int weight) {
        return FormElement.of(ElementType.NUMBER, WEIGHT)
                .label("Weight")
                .description("Lighter ones are listed first.")
                .value(weight)
                .rule(ValidationRule.pattern(BlockLayoutController.WHOLE_NUMBER)
                        .withMessage("The weight is a whole number."));
    }

    private String managePage(WorkflowConfig workflow, FormElement tree, Map<String, String> errors, Model model) {
        Function<String, String> stateLabel = stateId -> workflow.state(stateId)
                .map(WorkflowState::label).orElse(stateId);
        model.addAttribute("title", "Edit " + workflow.label());
        model.addAttribute("moderationPath", workflow.type().equals(ContentModerationWorkflowType.ID)
                ? ModerationController.settingsPath(workflow.id()) : "");
        model.addAttribute("typeLabel", workflows.hasType(workflow.type())
                ? workflows.type(workflow.type()).label() : workflow.type() + " (missing)");
        model.addAttribute("action", managePath(workflow.id()));
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        model.addAttribute("states", workflow.states());
        model.addAttribute("required", workflows.hasType(workflow.type())
                ? workflows.type(workflow.type()).requiredStates() : List.of());
        model.addAttribute("transitions", workflow.transitions().stream()
                .map(transition -> new WorkflowTransitionRow(transition.id(), transition.label(),
                        String.join(", ", transition.from().stream().map(stateLabel).toList()),
                        stateLabel.apply(transition.to()),
                        WorkflowManager.transitionPermission(workflow.id(), transition.id())))
                .toList());
        return "admin/workflow";
    }

    private String formPage(
            String title, String action, FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", title);
        model.addAttribute("description", "The states content moves through, and who may move it.");
        model.addAttribute("action", action);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/block-form";
    }

    private String confirmPage(String title, String action, String confirmLabel, String cancelPath, Model model) {
        FormElement confirm = FormElement.of(ElementType.CONTAINER, "confirm")
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "confirm").label(confirmLabel))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(cancelPath)));
        model.addAttribute("title", title);
        model.addAttribute("description", "This action cannot be undone.");
        model.addAttribute("action", action);
        model.addAttribute("formMarkup", renderer.render(confirm));
        return "admin/block-form";
    }

    private WorkflowConfig workflowOf(String id) {
        return workflows.find(id).orElseThrow(() -> new EntityNotFoundException("workflow", id));
    }

    private static WorkflowState stateOf(WorkflowConfig workflow, String state) {
        return workflow.state(state).orElseThrow(() -> new EntityNotFoundException("state", state));
    }

    /** A state the workflow's type does not require. */
    private WorkflowState removableStateOf(WorkflowConfig workflow, String state) {
        WorkflowState existing = stateOf(workflow, state);
        if (workflows.hasType(workflow.type()) && workflows.type(workflow.type()).requiredStates().contains(state)) {
            throw new EntityNotFoundException("removable state", state);
        }
        return existing;
    }

    private static WorkflowTransition transitionOf(WorkflowConfig workflow, String transition) {
        return workflow.transition(transition)
                .orElseThrow(() -> new EntityNotFoundException("transition", transition));
    }
}
