package dev.springdrop.web;

import dev.springdrop.kernel.access.AccessHelper;
import dev.springdrop.kernel.comment.CommentEntityType;
import dev.springdrop.kernel.comment.CommentFieldType;
import dev.springdrop.kernel.comment.CommentFormatter;
import dev.springdrop.kernel.comment.CommentPermissions;
import dev.springdrop.kernel.comment.CommentService;
import dev.springdrop.kernel.comment.CommentSettings;
import dev.springdrop.kernel.comment.CommentStatus;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.kernel.web.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;

/**
 * Writing, changing, and deleting comments. A comment is posted about an
 * entity through one of its comment fields while comments are open, by someone
 * who may post them, and is published straight away only for someone who may
 * skip approval. Someone not signed in leaves the contact details the field
 * asks for, and a field requiring preview saves only a comment that was
 * previewed first.
 */
@Controller
public class CommentController {

    public static final String SUBJECT = "subject";

    public static final String BODY = "comment_body";

    public static final String NAME = "name";

    public static final String MAIL = "mail";

    public static final String HOMEPAGE = "homepage";

    public static final String PUBLISHED = "published";

    /** What the Preview button submits as. */
    public static final String PREVIEW = "preview";

    /** Carried by a form that has been previewed, so a field requiring preview can save it. */
    public static final String PREVIEWED = "previewed";

    static final int SUBJECT_MAX_LENGTH = 64;

    static final int BODY_MAX_LENGTH = 65_535;

    static final int CONTACT_MAX_LENGTH = 255;

    private static final String HOMEPAGE_PATTERN = "https?://\\S+";

    private final CommentService comments;
    private final EntityCrudService entities;
    private final EntityTypeManager entityTypes;
    private final EntityAccessManager entityAccess;
    private final AccessHelper access;
    private final FieldConfigManager fields;
    private final UserAccountService accounts;
    private final FormBuilder forms;
    private final FormRenderer renderer;

    public CommentController(
            CommentService comments,
            EntityCrudService entities,
            EntityTypeManager entityTypes,
            EntityAccessManager entityAccess,
            AccessHelper access,
            FieldConfigManager fields,
            UserAccountService accounts,
            FormBuilder forms,
            FormRenderer renderer) {
        this.comments = comments;
        this.entities = entities;
        this.entityTypes = entityTypes;
        this.entityAccess = entityAccess;
        this.access = access;
        this.fields = fields;
        this.accounts = accounts;
        this.forms = forms;
        this.renderer = renderer;
    }

    /** Where a comment is read: its place on the page of what it is about. */
    @GetMapping("/comment/{id}")
    public String permalink(@PathVariable long id) {
        EntityData comment = permitted(id, EntityAccessHandler.VIEW);
        return "redirect:" + hostPath(comment) + "#comment-" + id;
    }

    @GetMapping("/comment/reply/{entityType}/{entityId}/{field}")
    public String replyForm(@PathVariable String entityType, @PathVariable long entityId,
            @PathVariable String field, Model model) {
        Reply reply = reply(entityType, entityId, field, CommentEntityType.NO_PARENT);
        return formPage(reply, replyForm(reply, Map.of(), false), Map.of(), "", model);
    }

    @GetMapping("/comment/reply/{entityType}/{entityId}/{field}/{parent}")
    public String replyToCommentForm(@PathVariable String entityType, @PathVariable long entityId,
            @PathVariable String field, @PathVariable long parent, Model model) {
        Reply reply = reply(entityType, entityId, field, parent);
        return formPage(reply, replyForm(reply, Map.of(), false), Map.of(), "", model);
    }

    @PostMapping("/comment/reply/{entityType}/{entityId}/{field}")
    public String post(@PathVariable String entityType, @PathVariable long entityId, @PathVariable String field,
            @RequestParam Map<String, String> submitted, HttpServletRequest request, Model model) {
        return post(reply(entityType, entityId, field, CommentEntityType.NO_PARENT), submitted, request, model);
    }

    @PostMapping("/comment/reply/{entityType}/{entityId}/{field}/{parent}")
    public String postReply(@PathVariable String entityType, @PathVariable long entityId,
            @PathVariable String field, @PathVariable long parent, @RequestParam Map<String, String> submitted,
            HttpServletRequest request, Model model) {
        return post(reply(entityType, entityId, field, parent), submitted, request, model);
    }

    @GetMapping("/comment/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        EntityData comment = permitted(id, EntityAccessHandler.UPDATE);
        return editPage(comment, editForm(comment, Map.of(
                SUBJECT, comment.label(),
                BODY, CommentService.textOf(comment)), CommentService.published(comment)), Map.of(), model);
    }

    @PostMapping("/comment/{id}/edit")
    public String edit(@PathVariable long id, @RequestParam Map<String, String> submitted, Model model) {
        EntityData comment = permitted(id, EntityAccessHandler.UPDATE);
        FormElement tree = editForm(comment, submitted, submitted.containsKey(PUBLISHED));
        FormState state = forms.validate(tree, new LinkedHashMap<>(submitted));
        if (state.hasErrors()) {
            return editPage(comment, tree, state.errors(), model);
        }
        Map<String, Object> values = new LinkedHashMap<>(comment.fields());
        values.put(CommentEntityType.BODY, submitted.getOrDefault(BODY, ""));
        if (access.has(CommentPermissions.ADMINISTER_COMMENTS)) {
            values.put(BaseFieldDefinition.STATUS, submitted.containsKey(PUBLISHED));
        }
        comments.update(new EntityData(CommentEntityType.ID, comment.id(), comment.uuid(), null,
                submitted.getOrDefault(SUBJECT, ""), comment.langcode(), null, values));
        return "redirect:" + CommentEntityType.path(id);
    }

    @GetMapping("/comment/{id}/delete")
    public String confirmDelete(@PathVariable long id, Model model) {
        EntityData comment = permitted(id, EntityAccessHandler.DELETE);
        FormElement confirm = FormElement.of(ElementType.CONTAINER, "confirm")
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "confirm").label("Delete comment"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel")
                                .value(hostPath(comment))));
        model.addAttribute("title", "Delete the comment " + comment.label() + "?");
        model.addAttribute("description", "Every reply to it is deleted too. This action cannot be undone.");
        model.addAttribute("action", CommentEntityType.path(id) + "/delete");
        model.addAttribute("formMarkup", renderer.render(confirm));
        return "admin/block-form";
    }

    @PostMapping("/comment/{id}/delete")
    public String delete(@PathVariable long id) {
        EntityData comment = permitted(id, EntityAccessHandler.DELETE);
        comments.delete(id);
        return "redirect:" + hostPath(comment);
    }

    /**
     * Saves the comment once the form's rules pass, the name someone not signed
     * in left is not an account's, and it was previewed where the field requires
     * it. Previewing shows the comment above the form instead of saving it.
     */
    private String post(Reply reply, Map<String, String> submitted, HttpServletRequest request, Model model) {
        boolean previewing = submitted.containsKey(PREVIEW);
        boolean previewed = previewing || submitted.containsKey(PREVIEWED);
        FormElement tree = replyForm(reply, submitted, previewed);
        FormState state = forms.validate(tree, new LinkedHashMap<>(submitted));
        String name = submitted.getOrDefault(NAME, "").strip();
        if (anonymous() && !name.isEmpty() && accounts.findByName(name).isPresent()) {
            state.error(NAME, "The name " + name + " belongs to a registered account.");
        }
        if (!state.hasErrors() && !previewed && reply.settings().preview() == CommentSettings.PREVIEW_REQUIRED) {
            state.error(BODY, "Preview the comment before saving it.");
        }
        if (state.hasErrors() || previewing) {
            String preview = state.hasErrors() ? "" : previewOf(submitted);
            return formPage(reply, tree, state.errors(), preview, model);
        }

        Map<String, Object> values = new LinkedHashMap<>(CommentService.draft(reply.host().entityType(),
                ((Number) reply.host().id()).longValue(), reply.field(), reply.parent()).fields());
        values.put(CommentEntityType.BODY, submitted.getOrDefault(BODY, ""));
        values.put(BaseFieldDefinition.OWNER, CurrentAccount.id());
        values.put(CommentEntityType.HOSTNAME, request.getRemoteAddr());
        values.put(BaseFieldDefinition.STATUS, access.has(CommentPermissions.SKIP_APPROVAL)
                || access.has(CommentPermissions.ADMINISTER_COMMENTS));
        if (anonymous()) {
            values.put(CommentEntityType.NAME, name);
            values.put(CommentEntityType.MAIL, submitted.getOrDefault(MAIL, "").strip());
            values.put(CommentEntityType.HOMEPAGE, submitted.getOrDefault(HOMEPAGE, "").strip());
        }
        EntityData saved = comments.post(EntityData.of(CommentEntityType.ID, null, null,
                submitted.getOrDefault(SUBJECT, ""), values));
        return "redirect:" + hostPath(saved) + "#comment-" + saved.id();
    }

    /**
     * The form for a new comment: contact details for someone not signed in when
     * the field asks for them, a subject, the comment, and Preview and Save as
     * the field's preview setting allows.
     */
    private FormElement replyForm(Reply reply, Map<String, String> submitted, boolean previewed) {
        FormElement form = FormElement.of(ElementType.CONTAINER, "comment");
        int contact = reply.settings().anonymous();
        if (anonymous() && contact != CommentSettings.CONTACT_NONE) {
            FormElement name = FormElement.of(ElementType.TEXTFIELD, NAME).label("Your name")
                    .value(submitted.getOrDefault(NAME, ""))
                    .rule(ValidationRule.maxLength(CONTACT_MAX_LENGTH));
            FormElement mail = FormElement.of(ElementType.TEXTFIELD, MAIL).label("Email")
                    .description("Not shown with the comment.")
                    .value(submitted.getOrDefault(MAIL, ""))
                    .rule(ValidationRule.maxLength(CONTACT_MAX_LENGTH))
                    .rule(ValidationRule.email());
            if (contact == CommentSettings.CONTACT_REQUIRED) {
                name.markRequired();
                mail.markRequired();
            }
            form.child(name).child(mail).child(FormElement.of(ElementType.TEXTFIELD, HOMEPAGE).label("Homepage")
                    .value(submitted.getOrDefault(HOMEPAGE, ""))
                    .rule(ValidationRule.maxLength(CONTACT_MAX_LENGTH))
                    .rule(ValidationRule.pattern(HOMEPAGE_PATTERN)
                            .withMessage("The homepage is a web address starting with http:// or https://.")));
        }
        form.child(subjectElement(submitted.getOrDefault(SUBJECT, "")))
                .child(bodyElement(submitted.getOrDefault(BODY, "")));
        if (previewed) {
            form.child(FormElement.of(ElementType.HIDDEN, PREVIEWED).value(FormRenderer.CHECKED_VALUE));
        }
        FormElement actions = FormElement.of(ElementType.ACTIONS, "actions");
        if (previewed || reply.settings().preview() != CommentSettings.PREVIEW_REQUIRED) {
            actions.child(FormElement.of(ElementType.SUBMIT, "save").label("Save"));
        }
        if (reply.settings().preview() != CommentSettings.PREVIEW_NONE) {
            actions.child(FormElement.of(ElementType.SUBMIT, PREVIEW).label("Preview"));
        }
        return form.child(actions.child(FormElement.of(ElementType.LINK, "cancel").label("Cancel")
                .value(canonical(reply.host().entityType(), reply.host().id()))));
    }

    private FormElement editForm(EntityData comment, Map<String, String> submitted, boolean published) {
        FormElement form = FormElement.of(ElementType.CONTAINER, "comment")
                .child(subjectElement(submitted.getOrDefault(SUBJECT, "")))
                .child(bodyElement(submitted.getOrDefault(BODY, "")));
        if (access.has(CommentPermissions.ADMINISTER_COMMENTS)) {
            form.child(FormElement.of(ElementType.CHECKBOX, PUBLISHED).label("Published").value(published));
        }
        return form.child(FormElement.of(ElementType.ACTIONS, "actions")
                .child(FormElement.of(ElementType.SUBMIT, "save").label("Save"))
                .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(hostPath(comment))));
    }

    private static FormElement subjectElement(String value) {
        return FormElement.of(ElementType.TEXTFIELD, SUBJECT).label("Subject")
                .description("Left empty, the start of the comment is used.")
                .value(value)
                .rule(ValidationRule.maxLength(SUBJECT_MAX_LENGTH));
    }

    private static FormElement bodyElement(String value) {
        return FormElement.of(ElementType.TEXTAREA, BODY).label("Comment").markRequired()
                .value(value)
                .rule(ValidationRule.maxLength(BODY_MAX_LENGTH));
    }

    private static String previewOf(Map<String, String> submitted) {
        String subject = submitted.getOrDefault(SUBJECT, "").strip();
        return "<div class=\"card mb-4 comment-preview\"><div class=\"card-body\">"
                + (subject.isEmpty() ? "" : "<h3 class=\"h6\">" + HtmlUtils.htmlEscape(subject) + "</h3>")
                + "<div>" + HtmlUtils.htmlEscape(submitted.getOrDefault(BODY, "")).replace("\n", "<br>") + "</div>"
                + "</div></div>";
    }

    private String formPage(Reply reply, FormElement tree, Map<String, String> errors, String preview,
            Model model) {
        model.addAttribute("title", reply.parent() == CommentEntityType.NO_PARENT
                ? "Add new comment" : "Reply to a comment");
        model.addAttribute("description", "About " + reply.host().label() + ".");
        model.addAttribute("action", replyAction(reply));
        model.addAttribute("formMarkup", preview + renderer.render(tree, errors));
        return "admin/block-form";
    }

    private String editPage(EntityData comment, FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", "Edit comment " + comment.label());
        model.addAttribute("description", "");
        model.addAttribute("action", CommentEntityType.path(comment.id()) + "/edit");
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/block-form";
    }

    /**
     * What a new comment is posted about, refused unless the entity exists and
     * may be read, the field is one of its comment fields and is open, the
     * person may post, and a comment being replied to belongs to the same thread
     * and is not as deep as the field allows.
     */
    private Reply reply(String entityType, long entityId, String field, long parent) {
        EntityType type = entityTypes.find(entityType)
                .orElseThrow(() -> new EntityNotFoundException("entity type", entityType));
        EntityData host = entities.load(type.id(), entityId)
                .orElseThrow(() -> new EntityNotFoundException(entityType, String.valueOf(entityId)));
        String bundle = Optional.ofNullable(host.bundle()).orElse(host.entityType());
        FieldInstanceConfig instance = fields.findInstance(entityType, bundle, field)
                .filter(found -> fields.findStorage(entityType, field)
                        .map(storage -> storage.type().equals(CommentFieldType.ID)).orElse(false))
                .orElseThrow(() -> new EntityNotFoundException("comment field", field));
        CommentSettings settings = CommentSettings.of(instance.settings());
        if (!entityAccess.may(entityType, host, EntityAccessHandler.VIEW)
                || CommentStatus.of(host.fields().get(field)) != CommentStatus.OPEN
                || !entityAccess.may(CommentEntityType.ID, CommentEntityType.ID, EntityAccessHandler.CREATE)) {
            throw new AccessDeniedException("You may not comment here.");
        }
        if (parent != CommentEntityType.NO_PARENT) {
            EntityData replied = comments.find(parent)
                    .filter(comment -> CommentService.belongsTo(comment, entityType, entityId, field))
                    .filter(comment -> entityAccess.may(CommentEntityType.ID, comment, EntityAccessHandler.VIEW))
                    .orElseThrow(() -> new EntityNotFoundException("comment", String.valueOf(parent)));
            if (!settings.allowsReplyAt(CommentService.depthOf(CommentService.threadOf(replied)))) {
                throw new AccessDeniedException("This comment cannot be replied to.");
            }
        }
        return new Reply(host, field, parent, settings);
    }

    /** What a new comment is about: the entity, the field, the comment it replies to, and the field's settings. */
    record Reply(EntityData host, String field, long parent, CommentSettings settings) {
    }

    private static String replyAction(Reply reply) {
        String base = CommentFormatter.replyPath(reply.host().entityType(), reply.host().id(), reply.field());
        return reply.parent() == CommentEntityType.NO_PARENT ? base : base + "/" + reply.parent();
    }

    /** The page of what a comment is about, from its type's canonical link. */
    private String hostPath(EntityData comment) {
        return canonical(String.valueOf(comment.fields().get(CommentEntityType.HOST_TYPE)),
                comment.fields().get(CommentEntityType.HOST_ID));
    }

    private String canonical(String entityType, Object id) {
        return entityTypes.require(entityType).canonicalPath(id);
    }

    private boolean anonymous() {
        return CurrentAccount.id() == UserAccount.ANONYMOUS_ID;
    }

    private EntityData permitted(long id, String operation) {
        EntityData comment = comments.find(id)
                .orElseThrow(() -> new EntityNotFoundException("comment", String.valueOf(id)));
        if (!entityAccess.may(CommentEntityType.ID, comment, operation)) {
            throw new AccessDeniedException("You may not " + operation + " this comment.");
        }
        return comment;
    }

}
