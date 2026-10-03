package dev.springdrop.web;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockPageRenderer;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.datetime.DateFormatService;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityRevision;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.display.ViewDisplayConfig;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeRevisionAccess;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.kernel.text.TextDiff;
import dev.springdrop.kernel.theme.PageChrome;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.kernel.web.EntityNotFoundException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * A node's revision history: listing its revisions, reading one, comparing two
 * word by word, reverting the node to one, and deleting one. Reverting saves
 * the earlier revision's content as a new revision, so the history only grows.
 * The revision the site shows can be neither reverted to nor deleted.
 */
@Controller
public class NodeRevisionController {

    static final String BASE = "/node/{id}/revisions";

    public static final String LEFT = "left";

    public static final String RIGHT = "right";

    private static final String DATE_FORMAT = "short";

    private final NodeService nodes;
    private final EntityCrudService entities;
    private final NodeRevisionAccess revisionAccess;
    private final FieldConfigManager fields;
    private final UserAccountService accounts;
    private final DateFormatService dates;
    private final FormRenderer renderer;
    private final ConfigStore configStore;
    private final BlockPageRenderer pages;

    public NodeRevisionController(
            NodeService nodes,
            EntityCrudService entities,
            NodeRevisionAccess revisionAccess,
            FieldConfigManager fields,
            UserAccountService accounts,
            DateFormatService dates,
            FormRenderer renderer,
            ConfigStore configStore,
            BlockPageRenderer pages) {
        this.nodes = nodes;
        this.entities = entities;
        this.revisionAccess = revisionAccess;
        this.fields = fields;
        this.accounts = accounts;
        this.dates = dates;
        this.renderer = renderer;
        this.configStore = configStore;
        this.pages = pages;
    }

    public static String historyPath(Object id) {
        return NodeEntityType.path(id) + "/revisions";
    }

    public static String revisionPath(Object id, long revisionId) {
        return historyPath(id) + "/" + revisionId;
    }

    @GetMapping(BASE)
    public String history(@PathVariable long id, Model model) {
        EntityData node = permitted(id, revisionAccess::mayView);
        boolean revertible = revisionAccess.mayRevert(node);
        boolean deletable = revisionAccess.mayDelete(node);

        List<NodeRevisionRow> rows = new ArrayList<>();
        for (EntityRevision revision : entities.revisions(NodeEntityType.ID, id)) {
            boolean current = revision.revisionId() == node.revisionId();
            rows.add(new NodeRevisionRow(revision.revisionId(), dateOf(revision), authorOf(revision),
                    String.valueOf(revision.baseValues().getOrDefault(NodeEntityType.REVISION_LOG, "")),
                    current, revertible && !current, deletable && !current));
        }
        model.addAttribute("title", "Revisions for " + node.label());
        model.addAttribute("rows", rows);
        model.addAttribute("nodePath", NodeEntityType.path(id));
        model.addAttribute("historyPath", historyPath(id));
        model.addAttribute("comparable", rows.size() > 1);
        return "node/revisions";
    }

    @GetMapping(value = BASE + "/{revision}/view", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String view(@PathVariable long id, @PathVariable long revision) {
        EntityData node = permitted(id, revisionAccess::mayView);
        EntityRevision chosen = revisionOf(id, revision);
        EntityData earlier = loaded(node, revision);
        String title = "Revision of " + earlier.label() + " from " + dateOf(chosen);
        SiteInformation site = configStore.read(
                SiteInformation.CONFIG_NAME, SiteInformation.class, SiteInformation.DEFAULTS);
        String path = revisionPath(id, revision) + "/view";
        return pages.render(PageChrome.of(site.name(), title),
                nodes.build(earlier, ViewDisplayConfig.FULL_MODE, true),
                BlockContext.of(path, title).withRouteEntity(earlier)).html();
    }

    /** The two revisions compared word by word, the older one on the left whichever order they were chosen in. */
    @GetMapping(BASE + "/diff")
    public String diff(@PathVariable long id, @RequestParam(name = LEFT) long left,
            @RequestParam(name = RIGHT) long right, Model model) {
        EntityData node = permitted(id, revisionAccess::mayView);
        long older = Math.min(left, right);
        long newer = Math.max(left, right);
        EntityData before = loaded(node, revisionOf(id, older).revisionId());
        EntityData after = loaded(node, revisionOf(id, newer).revisionId());

        List<NodeRevisionDiffRow> rows = new ArrayList<>();
        rows.add(row("Title", before.label(), after.label()));
        for (FieldInstanceConfig instance : fields.instances(NodeEntityType.ID, node.bundle())) {
            rows.add(row(instance.label(),
                    text(before.fields().get(instance.fieldName())), text(after.fields().get(instance.fieldName()))));
        }
        model.addAttribute("title", "Changes to " + node.label());
        model.addAttribute("description", "Revision " + older + " compared with revision " + newer + ".");
        model.addAttribute("rows", rows);
        model.addAttribute("historyPath", historyPath(id));
        return "node/revision-diff";
    }

    @GetMapping(BASE + "/{revision}/revert")
    public String confirmRevert(@PathVariable long id, @PathVariable long revision, Model model) {
        EntityData node = permitted(id, revisionAccess::mayRevert);
        EntityRevision chosen = earlierRevisionOf(node, revision);
        return confirmPage("Revert to the revision from " + dateOf(chosen) + "?",
                "The content of this revision is saved as a new revision, which the site then shows.",
                revisionPath(id, revision) + "/revert", "Revert", historyPath(id), model);
    }

    @PostMapping(BASE + "/{revision}/revert")
    public String revert(@PathVariable long id, @PathVariable long revision) {
        EntityData node = permitted(id, revisionAccess::mayRevert);
        EntityRevision chosen = earlierRevisionOf(node, revision);
        EntityData earlier = loaded(node, revision);

        Map<String, Object> values = new LinkedHashMap<>(earlier.fields());
        fields.fieldNames(NodeEntityType.ID, node.bundle()).forEach(field -> values.putIfAbsent(field, List.of()));
        EntityData copy = new EntityData(NodeEntityType.ID, node.id(), node.uuid(), node.bundle(), earlier.label(),
                node.langcode(), null, values);
        nodes.save(copy, CurrentAccount.id(), true, "Copy of the revision from " + dateOf(chosen) + ".");
        return "redirect:" + historyPath(id);
    }

    @GetMapping(BASE + "/{revision}/delete")
    public String confirmDelete(@PathVariable long id, @PathVariable long revision, Model model) {
        EntityData node = permitted(id, revisionAccess::mayDelete);
        EntityRevision chosen = earlierRevisionOf(node, revision);
        return confirmPage("Delete the revision from " + dateOf(chosen) + "?",
                "This action cannot be undone.", revisionPath(id, revision) + "/delete", "Delete revision",
                historyPath(id), model);
    }

    @PostMapping(BASE + "/{revision}/delete")
    public String delete(@PathVariable long id, @PathVariable long revision) {
        EntityData node = permitted(id, revisionAccess::mayDelete);
        earlierRevisionOf(node, revision);
        entities.deleteRevision(NodeEntityType.ID, id, revision);
        return "redirect:" + historyPath(id);
    }

    private String confirmPage(String title, String description, String action, String confirmLabel,
            String cancelPath, Model model) {
        FormElement confirm = FormElement.of(ElementType.CONTAINER, "confirm")
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "confirm").label(confirmLabel))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(cancelPath)));
        model.addAttribute("title", title);
        model.addAttribute("description", description);
        model.addAttribute("action", action);
        model.addAttribute("formMarkup", renderer.render(confirm));
        return "admin/block-form";
    }

    private static NodeRevisionDiffRow row(String label, String before, String after) {
        return new NodeRevisionDiffRow(label, !before.equals(after), TextDiff.words(before, after));
    }

    /** A field's values as text, one value after another. */
    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        return (value instanceof List<?> values)
                ? String.join(" ", values.stream().map(String::valueOf).toList())
                : String.valueOf(value);
    }

    private String dateOf(EntityRevision revision) {
        Object saved = revision.baseValues().get(NodeEntityType.REVISION_CREATED);
        return (saved instanceof OffsetDateTime timestamp)
                ? dates.format(timestamp.toInstant(), DATE_FORMAT, null)
                : "an unknown date";
    }

    private String authorOf(EntityRevision revision) {
        Object author = revision.baseValues().get(NodeEntityType.REVISION_USER);
        return (author instanceof Number id)
                ? accounts.find(id.longValue()).map(UserAccount::name).orElse(UserAccount.ANONYMOUS_NAME)
                : UserAccount.ANONYMOUS_NAME;
    }

    private EntityData loaded(EntityData node, long revisionId) {
        return entities.load(NodeEntityType.ID, node.id(), node.langcode(), revisionId).orElseThrow();
    }

    private EntityRevision revisionOf(long id, long revisionId) {
        return entities.revisions(NodeEntityType.ID, id).stream()
                .filter(revision -> revision.revisionId() == revisionId)
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("revision", String.valueOf(revisionId)));
    }

    /** A revision other than the one the site shows, which is the only kind that can be reverted to or deleted. */
    private EntityRevision earlierRevisionOf(EntityData node, long revisionId) {
        if (node.revisionId() == revisionId) {
            throw new EntityNotFoundException("earlier revision", String.valueOf(revisionId));
        }
        return revisionOf(((Number) node.id()).longValue(), revisionId);
    }

    private EntityData permitted(long id, Predicate<EntityData> allowed) {
        EntityData node = nodes.find(id).orElseThrow(() -> new EntityNotFoundException("node", String.valueOf(id)));
        if (!allowed.test(node)) {
            throw new AccessDeniedException("You may not do that with the revisions of this content.");
        }
        return node;
    }
}
