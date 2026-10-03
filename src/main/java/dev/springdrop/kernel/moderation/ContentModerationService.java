package dev.springdrop.kernel.moderation;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.workflow.WorkflowConfig;
import dev.springdrop.kernel.workflow.WorkflowManager;
import dev.springdrop.kernel.workflow.WorkflowState;
import dev.springdrop.kernel.workflow.WorkflowTransition;
import dev.springdrop.kernel.workflow.types.ContentModerationWorkflowType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Moving content through a content moderation workflow. A content type opts in
 * by being one of the workflow's bundles. Each save of moderated content moves
 * its newest revision into a state through a transition the person may use.
 *
 * <p>A state that does not make its revision the default, such as a draft,
 * saves a forward revision when the content is already published, so the
 * published revision stays live until another is published over it. Content
 * that has never been published has nothing live to keep, so its drafts are
 * the revision the site shows, unpublished.
 */
@Component
public class ContentModerationService {

    public static final String EDITORIAL = "editorial";

    public static final String ARCHIVED = "archived";

    private final WorkflowManager workflows;
    private final ConfigStore configStore;
    private final NodeService nodes;

    public ContentModerationService(WorkflowManager workflows, ConfigStore configStore, NodeService nodes) {
        this.workflows = workflows;
        this.configStore = configStore;
        this.nodes = nodes;
    }

    /**
     * Adds the Editorial workflow, moderating no content type yet: Draft,
     * Published, and Archived, with the transitions between them.
     */
    public void installEditorial() {
        if (workflows.find(EDITORIAL).isPresent()) {
            return;
        }
        String draft = ContentModerationWorkflowType.DRAFT;
        String published = ContentModerationWorkflowType.PUBLISHED;
        WorkflowConfig editorial = workflows.create(EDITORIAL, "Editorial", ContentModerationWorkflowType.ID)
                .withState(new WorkflowState(ARCHIVED, "Archived", 2))
                .withTransition(new WorkflowTransition("archive", "Archive", List.of(published), ARCHIVED, 2))
                .withTransition(new WorkflowTransition("archived_draft", "Restore to Draft", List.of(ARCHIVED),
                        draft, 3))
                .withTransition(new WorkflowTransition("archived_published", "Restore", List.of(ARCHIVED),
                        published, 4));
        workflows.save(editorial);
        saveConfig(new ModerationConfig(EDITORIAL, Map.of(
                draft, ModerationStateSettings.UNPUBLISHED,
                published, new ModerationStateSettings(true, true),
                ARCHIVED, new ModerationStateSettings(false, true)), List.of()));
    }

    public Optional<ModerationConfig> config(String workflowId) {
        return Optional.ofNullable(configStore.read(
                ModerationConfig.configName(workflowId), ModerationConfig.class, null));
    }

    public void saveConfig(ModerationConfig config) {
        configStore.save(ModerationConfig.configName(config.workflowId()), config);
    }

    /** What a content moderation workflow's settings are, or none yet for a new one. */
    public ModerationConfig configOrDefaults(WorkflowConfig workflow) {
        return config(workflow.id()).orElseGet(() -> new ModerationConfig(workflow.id(), Map.of(
                ContentModerationWorkflowType.PUBLISHED, new ModerationStateSettings(true, true)), List.of()));
    }

    /** The workflow moderating a content type, if one does. */
    public Optional<Moderation> moderationOf(String bundle) {
        for (WorkflowConfig workflow : workflows.all()) {
            if (workflow.type().equals(ContentModerationWorkflowType.ID)) {
                ModerationConfig config = configOrDefaults(workflow);
                if (config.bundles().contains(bundle)) {
                    return Optional.of(new Moderation(workflow, config));
                }
            }
        }
        return Optional.empty();
    }

    /** The state a node's revision is in, which for one never moderated is the draft state. */
    public static String stateOf(EntityData node) {
        Object state = node.fields().get(NodeEntityType.MODERATION_STATE);
        return (state == null) ? ContentModerationWorkflowType.DRAFT : state.toString();
    }

    /** The states someone may move a revision into from the state it is in. */
    public List<WorkflowState> reachableStates(Moderation moderation, String fromState, Authentication who) {
        return workflows.usable(moderation.workflow(), fromState, who).stream()
                .map(transition -> moderation.workflow().state(transition.to()).orElseThrow())
                .distinct()
                .toList();
    }

    /** Whether the node has a revision ahead of the one the site shows. */
    public boolean hasForwardRevision(EntityData node) {
        return nodes.latestRevision(node).revisionId() > node.revisionId();
    }

    /**
     * Saves a new revision of a moderated node in the given state. The move has
     * to be one the workflow makes and the person may use. The revision is
     * published as the state says, and becomes the one the site shows unless it
     * is a forward revision ahead of a published one.
     *
     * @param live the revision the site shows, or nothing for a node being created
     */
    public EntityData save(Moderation moderation, EntityData candidate, Optional<EntityData> live,
            String toState, long accountId, String log, Authentication who) {
        Optional<String> refusal = workflows.refusal(moderation.workflow(), stateOf(candidate), toState, who);
        if (refusal.isPresent()) {
            throw new ModerationRefusedException(refusal.get());
        }

        ModerationStateSettings settings = moderation.config().settings(toState);
        Map<String, Object> fields = new LinkedHashMap<>(candidate.fields());
        fields.put(NodeEntityType.MODERATION_STATE, toState);
        fields.put(BaseFieldDefinition.STATUS, settings.published());
        EntityData moved = candidate.withFields(fields);

        boolean livePublished = live.map(revision -> Boolean.TRUE.equals(
                revision.fields().get(BaseFieldDefinition.STATUS))).orElse(false);
        return (!settings.defaultRevision() && livePublished)
                ? nodes.saveForwardRevision(moved, accountId, log)
                : nodes.save(moved, accountId, true, log);
    }
}
