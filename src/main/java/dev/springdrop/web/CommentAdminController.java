package dev.springdrop.web;

import dev.springdrop.kernel.comment.CommentAuthors;
import dev.springdrop.kernel.comment.CommentEntityType;
import dev.springdrop.kernel.comment.CommentService;
import dev.springdrop.kernel.datetime.DateFormatService;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.theme.Tab;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The comment admin: published comments, and the comments waiting for
 * approval, each list with a bulk action applied to the comments ticked in it.
 * Deleting asks first, listing what will go.
 */
@Controller
public class CommentAdminController {

    public static final String PATH = "/admin/content/comment";

    public static final String APPROVAL_PATH = PATH + "/approval";

    public static final String OPERATION = "operation";

    /** The prefix of each row's box, followed by the comment's id. */
    public static final String ROW_PREFIX = "comment_";

    /** Carried by the delete confirmation, so the second submission deletes. */
    public static final String CONFIRMED = "confirmed";

    public static final String APPROVE = "approve";

    public static final String UNPUBLISH = "unpublish";

    public static final String DELETE = "delete";

    public static final String NOTHING_CHOSEN = "Choose at least one comment and an action.";

    private static final String DATE_FORMAT = "short";

    private static final List<SelectOption> PUBLISHED_ACTIONS = List.of(
            new SelectOption(UNPUBLISH, "Unpublish the selected comments"),
            new SelectOption(DELETE, "Delete the selected comments"));

    private static final List<SelectOption> APPROVAL_ACTIONS = List.of(
            new SelectOption(APPROVE, "Publish the selected comments"),
            new SelectOption(DELETE, "Delete the selected comments"));

    private final CommentService comments;
    private final CommentAuthors authors;
    private final EntityCrudService entities;
    private final EntityTypeManager entityTypes;
    private final DateFormatService dates;

    public CommentAdminController(
            CommentService comments,
            CommentAuthors authors,
            EntityCrudService entities,
            EntityTypeManager entityTypes,
            DateFormatService dates) {
        this.comments = comments;
        this.authors = authors;
        this.entities = entities;
        this.entityTypes = entityTypes;
        this.dates = dates;
    }

    @GetMapping(PATH)
    public String published(Model model) {
        return listPage(PATH, comments.published(), PUBLISHED_ACTIONS, "", model);
    }

    @GetMapping(APPROVAL_PATH)
    public String unapproved(Model model) {
        return listPage(APPROVAL_PATH, comments.unapproved(), APPROVAL_ACTIONS, "", model);
    }

    @PostMapping(PATH)
    public String applyToPublished(@RequestParam Map<String, String> submitted, Model model) {
        return apply(PATH, comments.published(), PUBLISHED_ACTIONS, submitted, model);
    }

    @PostMapping(APPROVAL_PATH)
    public String applyToUnapproved(@RequestParam Map<String, String> submitted, Model model) {
        return apply(APPROVAL_PATH, comments.unapproved(), APPROVAL_ACTIONS, submitted, model);
    }

    /**
     * Applies the chosen action to the ticked comments the list holds. A tick
     * on a comment the list does not hold is ignored. Nothing ticked, or an
     * action the list does not offer, changes nothing and says so. Deleting
     * shows what will be deleted and waits for the confirmation.
     */
    private String apply(String path, List<EntityData> listed, List<SelectOption> actions,
            Map<String, String> submitted, Model model) {
        List<EntityData> chosen = listed.stream()
                .filter(comment -> submitted.containsKey(ROW_PREFIX + comment.id()))
                .toList();
        String operation = submitted.getOrDefault(OPERATION, "");
        boolean offered = actions.stream().anyMatch(action -> action.value().equals(operation));
        if (chosen.isEmpty() || !offered) {
            return listPage(path, listed, actions, NOTHING_CHOSEN, model);
        }
        if (operation.equals(DELETE) && !submitted.containsKey(CONFIRMED)) {
            model.addAttribute("title", "Delete these comments?");
            model.addAttribute("rows", chosen.stream().map(this::row).toList());
            model.addAttribute("action", path);
            model.addAttribute("rowPrefix", ROW_PREFIX);
            model.addAttribute("cancelPath", path);
            return "admin/comment-delete";
        }
        for (EntityData comment : chosen) {
            long id = ((Number) comment.id()).longValue();
            switch (operation) {
                case APPROVE -> comments.approve(id);
                case UNPUBLISH -> unpublish(comment);
                default -> comments.delete(id);
            }
        }
        return "redirect:" + path;
    }

    private void unpublish(EntityData comment) {
        Map<String, Object> fields = new LinkedHashMap<>(comment.fields());
        fields.put(BaseFieldDefinition.STATUS, false);
        comments.update(comment.withFields(fields));
    }

    private String listPage(String path, List<EntityData> listed, List<SelectOption> actions, String error,
            Model model) {
        model.addAttribute("title", "Comments");
        model.addAttribute("tabs", List.of(
                new Tab("Published comments", PATH, path.equals(PATH)),
                new Tab("Unapproved comments", APPROVAL_PATH, path.equals(APPROVAL_PATH))));
        model.addAttribute("rows", listed.stream().map(this::row).toList());
        model.addAttribute("operations", actions);
        model.addAttribute("action", path);
        model.addAttribute("rowPrefix", ROW_PREFIX);
        model.addAttribute("error", error);
        return "admin/comments";
    }

    private CommentAdminRow row(EntityData comment) {
        String hostType = String.valueOf(comment.fields().get(CommentEntityType.HOST_TYPE));
        Object hostId = comment.fields().get(CommentEntityType.HOST_ID);
        Object changed = comment.fields().get(BaseFieldDefinition.CHANGED);
        return new CommentAdminRow(
                ((Number) comment.id()).longValue(),
                comment.label(),
                authors.nameOf(comment),
                entities.load(hostType, hostId).map(EntityData::label).orElse(""),
                entityTypes.require(hostType).canonicalPath(hostId),
                (changed instanceof OffsetDateTime timestamp) ? dates.format(timestamp.toInstant(), DATE_FORMAT, null)
                        : "");
    }
}
