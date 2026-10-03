package dev.springdrop.kernel.comment;

import dev.springdrop.kernel.access.AccessHelper;
import dev.springdrop.kernel.datetime.DateFormatService;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.field.formatter.FieldFormatter;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.render.RenderService;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.theme.TemplateSuggestions;
import dev.springdrop.kernel.theme.ThemeService;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.util.HtmlUtils;

/**
 * The comments on the entity the field belongs to, in a section under it: the
 * thread the person may read, each comment with the actions they may take, and
 * a button to add one while comments are open and they may post. A hidden
 * field, or a field drawn without its entity, draws nothing.
 */
@SpringDropPlugin(id = CommentFormatter.ID, type = FieldFormatter.class)
public class CommentFormatter implements FieldFormatter {

    public static final String ID = "comment_default";

    public static final String TEMPLATE_DIRECTORY = "comment";

    private static final String DATE_FORMAT = "medium";

    private final CommentService comments;
    private final EntityAccessManager entityAccess;
    private final AccessHelper access;
    private final ThemeService themes;
    private final RenderService renderer;
    private final CommentAuthors authors;
    private final DateFormatService dates;

    public CommentFormatter(
            CommentService comments,
            EntityAccessManager entityAccess,
            AccessHelper access,
            ThemeService themes,
            RenderService renderer,
            CommentAuthors authors,
            DateFormatService dates) {
        this.comments = comments;
        this.entityAccess = entityAccess;
        this.access = access;
        this.themes = themes;
        this.renderer = renderer;
        this.authors = authors;
        this.dates = dates;
    }

    @Override
    public String id() {
        return ID;
    }

    public static String replyPath(String hostType, Object hostId, String field) {
        return "/comment/reply/" + hostType + "/" + hostId + "/" + field;
    }

    @Override
    public String render(FormatterContext context, Object value) {
        CommentStatus status = CommentStatus.of(value);
        if (status == CommentStatus.HIDDEN || context.entity().isEmpty()) {
            return "";
        }
        EntityData host = context.entity().get();
        CommentSettings settings = CommentSettings.of(context.instance().settings());
        boolean open = status == CommentStatus.OPEN && access.has(CommentPermissions.POST_COMMENTS);
        String replyBase = replyPath(host.entityType(), host.id(), context.fieldName());

        Renderable section = themes.build(TEMPLATE_DIRECTORY, TemplateSuggestions.of("comments"), Map.of(
                "addUrl", open ? replyBase : ""));
        boolean administers = access.has(CommentPermissions.ADMINISTER_COMMENTS);
        for (CommentThreadItem item : comments.thread(host.entityType(), ((Number) host.id()).longValue(),
                context.fieldName(), settings.threaded(), administers)) {
            if (administers || entityAccess.may(CommentEntityType.ID, item.comment(), EntityAccessHandler.VIEW)) {
                section = section.child(comment(item, open && settings.allowsReplyAt(item.depth()), replyBase));
            }
        }
        return renderer.render(section).html();
    }

    private Renderable comment(CommentThreadItem item, boolean repliable, String replyBase) {
        EntityData comment = item.comment();
        Object id = comment.id();
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("id", id);
        variables.put("depth", item.depth());
        variables.put("subject", comment.label());
        variables.put("author", authors.nameOf(comment));
        variables.put("date", dateOf(comment));
        variables.put("published", CommentService.published(comment));
        variables.put("body", HtmlUtils.htmlEscape(CommentService.textOf(comment)).replace("\n", "<br>"));
        variables.put("replyUrl", repliable ? replyBase + "/" + id : "");
        variables.put("editUrl", entityAccess.may(CommentEntityType.ID, comment, EntityAccessHandler.UPDATE)
                ? CommentEntityType.path(id) + "/edit" : "");
        variables.put("deleteUrl", entityAccess.may(CommentEntityType.ID, comment, EntityAccessHandler.DELETE)
                ? CommentEntityType.path(id) + "/delete" : "");
        return themes.build(TEMPLATE_DIRECTORY, TemplateSuggestions.of("comment"), variables);
    }

    private String dateOf(EntityData comment) {
        return (comment.fields().get(BaseFieldDefinition.CREATED) instanceof OffsetDateTime created)
                ? dates.format(created.toInstant(), DATE_FORMAT, null)
                : "";
    }
}
