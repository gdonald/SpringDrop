package dev.springdrop.web;

import dev.springdrop.kernel.datetime.DateFormatService;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.entity.query.Sort;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.moderation.ContentModerationService;
import dev.springdrop.kernel.moderation.Moderation;
import dev.springdrop.kernel.moderation.ModerationConfig;
import dev.springdrop.kernel.moderation.ModerationStateSettings;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.kernel.web.EntityNotFoundException;
import dev.springdrop.kernel.workflow.WorkflowConfig;
import dev.springdrop.kernel.workflow.WorkflowManager;
import dev.springdrop.kernel.workflow.WorkflowState;
import dev.springdrop.kernel.workflow.types.ContentModerationWorkflowType;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Content moderation: what each state of a content moderation workflow means
 * and which content types it moderates, and the dashboard of moderated content
 * by the state its newest revision is in.
 */
@Controller
public class ModerationController {

    public static final String DASHBOARD_PATH = "/admin/content/moderated";

    public static final String STATE = "state";

    /** The prefix of each state's published box, followed by the state's id. */
    public static final String PUBLISHED_PREFIX = "published_";

    /** The prefix of each state's default-revision box, followed by the state's id. */
    public static final String DEFAULT_PREFIX = "default_";

    /** The prefix of each content type's box, followed by the type's id. */
    public static final String BUNDLE_PREFIX = "bundle_";

    private static final String DATE_FORMAT = "short";

    private final ContentModerationService moderations;
    private final WorkflowManager workflows;
    private final NodeTypeManager types;
    private final NodeService nodes;
    private final EntityQueryExecutor queries;
    private final EntityAccessManager entityAccess;
    private final UserAccountService accounts;
    private final DateFormatService dates;
    private final FormBuilder forms;
    private final FormRenderer renderer;

    public ModerationController(
            ContentModerationService moderations,
            WorkflowManager workflows,
            NodeTypeManager types,
            NodeService nodes,
            EntityQueryExecutor queries,
            EntityAccessManager entityAccess,
            UserAccountService accounts,
            DateFormatService dates,
            FormBuilder forms,
            FormRenderer renderer) {
        this.moderations = moderations;
        this.workflows = workflows;
        this.types = types;
        this.nodes = nodes;
        this.queries = queries;
        this.entityAccess = entityAccess;
        this.accounts = accounts;
        this.dates = dates;
        this.forms = forms;
        this.renderer = renderer;
    }

    public static String settingsPath(String workflowId) {
        return WorkflowController.managePath(workflowId) + "/moderation";
    }

    @GetMapping(WorkflowController.PATH + "/manage/{id}/moderation")
    public String settingsForm(@PathVariable String id, Model model) {
        WorkflowConfig workflow = moderationWorkflow(id);
        return settingsPage(workflow, settingsForm(workflow, moderations.configOrDefaults(workflow)), Map.of(),
                model);
    }

    /**
     * Saves what each state means and which content types the workflow
     * moderates. A published state has to make its revision the one the site
     * shows, and a content type another workflow moderates cannot be added.
     */
    @PostMapping(WorkflowController.PATH + "/manage/{id}/moderation")
    public String saveSettings(@PathVariable String id, @RequestParam Map<String, String> submitted, Model model) {
        WorkflowConfig workflow = moderationWorkflow(id);
        Map<String, ModerationStateSettings> states = new LinkedHashMap<>();
        for (WorkflowState state : workflow.states()) {
            states.put(state.id(), new ModerationStateSettings(
                    submitted.containsKey(PUBLISHED_PREFIX + state.id()),
                    submitted.containsKey(DEFAULT_PREFIX + state.id())));
        }
        List<String> bundles = types.all().stream()
                .map(NodeType::id)
                .filter(type -> submitted.containsKey(BUNDLE_PREFIX + type))
                .toList();
        ModerationConfig config = moderations.configOrDefaults(workflow).withStates(states).withBundles(bundles);

        FormElement tree = settingsForm(workflow, config);
        FormState state = forms.validate(tree, new LinkedHashMap<>(submitted));
        for (String bundle : bundles) {
            Optional<Moderation> elsewhere = moderations.moderationOf(bundle)
                    .filter(moderation -> !moderation.workflow().id().equals(id));
            elsewhere.ifPresent(moderation -> state.error(BUNDLE_PREFIX + bundle,
                    "The " + moderation.workflow().label() + " workflow already moderates this content type."));
        }
        if (state.hasErrors()) {
            return settingsPage(workflow, tree, state.errors(), model);
        }
        moderations.saveConfig(config);
        return "redirect:" + settingsPath(id);
    }

    /**
     * Moderated content by the state its newest revision is in. Without a state
     * chosen it lists everything whose newest revision is not published.
     */
    @GetMapping(DASHBOARD_PATH)
    public String dashboard(@RequestParam(name = STATE, defaultValue = "") String stateFilter, Model model) {
        List<ModeratedContentRow> rows = new ArrayList<>();
        Map<String, String> stateLabels = new LinkedHashMap<>();
        for (NodeType type : types.all()) {
            Optional<Moderation> moderation = moderations.moderationOf(type.id());
            if (moderation.isEmpty()) {
                continue;
            }
            moderation.get().workflow().states().forEach(state -> stateLabels.putIfAbsent(state.id(), state.label()));
            for (Object id : queries.query(NodeEntityType.ID)
                    .condition(Condition.equal(NodeEntityType.BUNDLE_KEY, type.id()))
                    .sort(Sort.descending(BaseFieldDefinition.CHANGED))
                    .ids()) {
                EntityData node = nodes.find(((Number) id).longValue()).orElseThrow();
                EntityData latest = nodes.latestRevision(node);
                String state = ContentModerationService.stateOf(latest);
                boolean listed = stateFilter.isEmpty()
                        ? !state.equals(ContentModerationWorkflowType.PUBLISHED)
                        : state.equals(stateFilter);
                if (listed) {
                    rows.add(row(type, moderation.get(), node, latest, state));
                }
            }
        }
        model.addAttribute("title", "Moderated content");
        model.addAttribute("rows", rows);
        model.addAttribute("states", stateLabels.entrySet().stream()
                .map(entry -> new SelectOption(entry.getKey(), entry.getValue()))
                .toList());
        model.addAttribute("chosenState", stateFilter);
        model.addAttribute("action", DASHBOARD_PATH);
        return "admin/moderated-content";
    }

    private ModeratedContentRow row(NodeType type, Moderation moderation, EntityData node, EntityData latest,
            String state) {
        long id = ((Number) node.id()).longValue();
        boolean ahead = !latest.revisionId().equals(node.revisionId());
        Object author = latest.fields().get(NodeEntityType.REVISION_USER);
        Object changed = latest.fields().get(BaseFieldDefinition.CHANGED);
        return new ModeratedContentRow(
                id,
                latest.label(),
                ahead ? NodeController.latestPath(id) : NodeEntityType.path(id),
                type.label(),
                moderation.workflow().state(state).map(WorkflowState::label).orElse(state),
                (author instanceof Number account)
                        ? accounts.find(account.longValue()).map(UserAccount::name).orElse(UserAccount.ANONYMOUS_NAME)
                        : UserAccount.ANONYMOUS_NAME,
                (changed instanceof OffsetDateTime timestamp)
                        ? dates.format(timestamp.toInstant(), DATE_FORMAT, null) : "",
                entityAccess.may(NodeEntityType.ID, node, EntityAccessHandler.UPDATE));
    }

    private FormElement settingsForm(WorkflowConfig workflow, ModerationConfig config) {
        FormElement statesSet = FormElement.of(ElementType.FIELDSET, "states").label("States");
        for (WorkflowState state : workflow.states()) {
            ModerationStateSettings settings = config.settings(state.id());
            statesSet.child(FormElement.of(ElementType.FIELDSET, "state_" + state.id()).label(state.label())
                    .child(FormElement.of(ElementType.CHECKBOX, PUBLISHED_PREFIX + state.id())
                            .label("Published")
                            .description("Content in this state is published.")
                            .value(settings.published()))
                    .child(FormElement.of(ElementType.CHECKBOX, DEFAULT_PREFIX + state.id())
                            .label("Default revision")
                            .description("A revision moved into this state becomes the one the site shows. "
                                    + "A published state has to be.")
                            .value(settings.defaultRevision())
                            .requiredWhen(PUBLISHED_PREFIX + state.id(), FormRenderer.CHECKED_VALUE)));
        }
        FormElement bundlesSet = FormElement.of(ElementType.FIELDSET, "bundles").label("Content types moderated");
        for (NodeType type : types.all()) {
            FormElement box = FormElement.of(ElementType.CHECKBOX, BUNDLE_PREFIX + type.id())
                    .label(type.label())
                    .value(config.bundles().contains(type.id()));
            moderations.moderationOf(type.id())
                    .filter(moderation -> !moderation.workflow().id().equals(workflow.id()))
                    .ifPresent(moderation -> box
                            .description("Moderated by the " + moderation.workflow().label() + " workflow.")
                            .attribute("disabled", "disabled"));
            bundlesSet.child(box);
        }
        return FormElement.of(ElementType.CONTAINER, "moderation")
                .child(statesSet)
                .child(bundlesSet)
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save moderation settings"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel")
                                .value(WorkflowController.managePath(workflow.id()))));
    }

    private String settingsPage(WorkflowConfig workflow, FormElement tree, Map<String, String> errors,
            Model model) {
        model.addAttribute("title", "Moderation settings for " + workflow.label());
        model.addAttribute("description", "What each state means for content, and the content types it moderates.");
        model.addAttribute("action", settingsPath(workflow.id()));
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/block-form";
    }

    private WorkflowConfig moderationWorkflow(String id) {
        return workflows.find(id)
                .filter(workflow -> workflow.type().equals(ContentModerationWorkflowType.ID))
                .orElseThrow(() -> new EntityNotFoundException("content moderation workflow", id));
    }
}
