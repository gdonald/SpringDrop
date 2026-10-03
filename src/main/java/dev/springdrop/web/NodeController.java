package dev.springdrop.web;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockPageRenderer;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityValidationException;
import dev.springdrop.kernel.field.FieldConstraintProvider;
import dev.springdrop.kernel.field.display.FormDisplayConfig;
import dev.springdrop.kernel.field.display.FormDisplayManager;
import dev.springdrop.kernel.field.display.ViewDisplayConfig;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.menu.BreadcrumbBuilder;
import dev.springdrop.kernel.moderation.ContentModerationService;
import dev.springdrop.kernel.moderation.Moderation;
import dev.springdrop.kernel.moderation.ModerationRefusedException;
import dev.springdrop.kernel.menu.MenuNavigation;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeLayoutAccess;
import dev.springdrop.kernel.node.NodeRevisionAccess;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.path.AliasPatternManager;
import dev.springdrop.kernel.path.PathAliasManager;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.kernel.theme.PageChrome;
import dev.springdrop.kernel.theme.Tab;
import dev.springdrop.kernel.validation.ConstraintViolation;
import dev.springdrop.kernel.web.EntityNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Writing and reading nodes. Each step asks the node's access rules first, so
 * a request someone may not make is refused before anything is shown or saved.
 */
@Controller
public class NodeController {

    public static final String ADD_PATH = "/node/add";

    public static final String TITLE = "title";

    public static final String NEW_REVISION = "new_revision";

    public static final String REVISION_LOG = "revision_log";

    public static final String MODERATION_STATE = "moderation_state";

    /** The URL alias the node's page answers at. */
    public static final String PATH = "path";

    /** Whether the alias is made from the content type's pattern. */
    public static final String PATH_AUTO = "path_auto";

    static final int TITLE_MAX_LENGTH = 255;

    static final int REVISION_LOG_MAX_LENGTH = 4000;

    private final NodeTypeManager types;
    private final NodeService nodes;
    private final EntityAccessManager entityAccess;
    private final FormDisplayManager formDisplays;
    private final FormBuilder forms;
    private final FormRenderer renderer;
    private final ConfigStore configStore;
    private final BlockPageRenderer pages;
    private final MenuNavigation navigation;
    private final BreadcrumbBuilder breadcrumbs;
    private final NodeLayoutAccess layoutAccess;
    private final NodeRevisionAccess revisionAccess;
    private final ContentModerationService moderations;
    private final PathAliasManager aliases;
    private final AliasPatternManager patterns;

    public NodeController(
            NodeTypeManager types,
            NodeService nodes,
            EntityAccessManager entityAccess,
            FormDisplayManager formDisplays,
            FormBuilder forms,
            FormRenderer renderer,
            ConfigStore configStore,
            BlockPageRenderer pages,
            MenuNavigation navigation,
            BreadcrumbBuilder breadcrumbs,
            NodeLayoutAccess layoutAccess,
            NodeRevisionAccess revisionAccess,
            ContentModerationService moderations,
            PathAliasManager aliases,
            AliasPatternManager patterns) {
        this.types = types;
        this.nodes = nodes;
        this.entityAccess = entityAccess;
        this.formDisplays = formDisplays;
        this.forms = forms;
        this.renderer = renderer;
        this.configStore = configStore;
        this.pages = pages;
        this.navigation = navigation;
        this.breadcrumbs = breadcrumbs;
        this.layoutAccess = layoutAccess;
        this.revisionAccess = revisionAccess;
        this.moderations = moderations;
        this.aliases = aliases;
        this.patterns = patterns;
    }

    /** The content types the person may create content of. */
    @GetMapping(ADD_PATH)
    public String chooseType(Model model) {
        List<NodeType> creatable = types.all().stream()
                .filter(type -> entityAccess.may(NodeEntityType.ID, type.id(), EntityAccessHandler.CREATE))
                .toList();
        model.addAttribute("title", "Add content");
        model.addAttribute("types", creatable);
        model.addAttribute("addPath", ADD_PATH + "/");
        return "node/add";
    }

    @GetMapping(ADD_PATH + "/{type}")
    public String addForm(@PathVariable String type, Model model) {
        NodeType contentType = creatableType(type);
        EntityData blank = nodes.create(contentType, CurrentAccount.id());
        return formPage(contentType, "Create " + contentType.label(), ADD_PATH + "/" + type,
                nodeForm(contentType, blank, true, "", stateElement(blank, ContentModerationService.stateOf(blank)), "",
                        hasPattern(blank)),
                Map.of(), model);
    }

    @PostMapping(ADD_PATH + "/{type}")
    public String add(@PathVariable String type, @RequestParam Map<String, String> submitted, Model model) {
        NodeType contentType = creatableType(type);
        EntityData blank = nodes.create(contentType, CurrentAccount.id());
        return save(contentType, blank, Optional.empty(), submitted, "Create " + contentType.label(),
                ADD_PATH + "/" + type, model);
    }

    @GetMapping(value = "/node/{id}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String view(@PathVariable long id) {
        EntityData node = permitted(id, EntityAccessHandler.VIEW);
        String path = NodeEntityType.path(id);
        SiteInformation site = configStore.read(
                SiteInformation.CONFIG_NAME, SiteInformation.class, SiteInformation.DEFAULTS);

        PageChrome chrome = PageChrome.of(site.name(), node.label())
                .withSlogan(site.slogan())
                .withPrimaryNavigation(navigation.primary(path))
                .withBreadcrumbs(breadcrumbs.build(path))
                .withTabs(tabs(node));
        BlockContext context = BlockContext.of(path, node.label()).withRouteEntity(node);
        return pages.render(chrome, nodes.build(node, ViewDisplayConfig.FULL_MODE, true), context).html();
    }

    /** The newest revision of the node when it is ahead of the one the site shows. */
    @GetMapping(value = "/node/{id}/latest", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String latest(@PathVariable long id) {
        EntityData node = nodes.find(id).orElseThrow(() -> new EntityNotFoundException("node", String.valueOf(id)));
        if (!revisionAccess.mayViewLatestVersion(node)) {
            throw new AccessDeniedException("You may not read the latest version of this content.");
        }
        if (!moderations.hasForwardRevision(node)) {
            throw new EntityNotFoundException("latest version", String.valueOf(id));
        }
        EntityData latest = nodes.latestRevision(node);
        String path = latestPath(id);
        SiteInformation site = configStore.read(
                SiteInformation.CONFIG_NAME, SiteInformation.class, SiteInformation.DEFAULTS);
        PageChrome chrome = PageChrome.of(site.name(), latest.label()).withTabs(tabs(node, path));
        BlockContext context = BlockContext.of(path, latest.label()).withRouteEntity(latest);
        return pages.render(chrome, nodes.build(latest, ViewDisplayConfig.FULL_MODE, true), context).html();
    }

    /**
     * The form for an existing node. A moderated node is edited from its newest
     * revision, which may be a draft ahead of the one the site shows.
     */
    @GetMapping("/node/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        EntityData node = permitted(id, EntityAccessHandler.UPDATE);
        NodeType contentType = typeOf(node);
        EntityData base = editedRevision(node);
        return formPage(contentType, "Edit " + base.label(), NodeEntityType.editPath(id),
                nodeForm(contentType, base, contentType.newRevision(), "",
                        stateElement(base, ContentModerationService.stateOf(base)),
                        aliases.aliasOf(NodeEntityType.path(id), base.langcode()).orElse(""),
                        hasPattern(base) && (aliases.aliasOf(NodeEntityType.path(id), base.langcode()).isEmpty()
                                || aliases.generated(NodeEntityType.path(id), base.langcode()))),
                Map.of(), model);
    }

    @PostMapping("/node/{id}/edit")
    public String edit(@PathVariable long id, @RequestParam Map<String, String> submitted, Model model) {
        EntityData node = permitted(id, EntityAccessHandler.UPDATE);
        EntityData base = editedRevision(node);
        return save(typeOf(node), base, Optional.of(node), submitted, "Edit " + base.label(),
                NodeEntityType.editPath(id), model);
    }

    public static String latestPath(Object id) {
        return NodeEntityType.path(id) + "/latest";
    }

    private EntityData editedRevision(EntityData node) {
        return moderations.moderationOf(node.bundle()).isPresent() ? nodes.latestRevision(node) : node;
    }

    /**
     * Saves the node once the form's rules and the node's own constraints pass,
     * then shows it. Otherwise the form comes back holding what was submitted,
     * with each error on its field. A field the form shows and the submission
     * leaves empty is emptied, rather than keeping what it held.
     */
    private String save(NodeType type, EntityData base, Optional<EntityData> live, Map<String, String> submitted,
            String title, String action, Model model) {

        Map<String, Object> values = new LinkedHashMap<>(submitted);
        Map<String, Object> fields = new LinkedHashMap<>(base.fields());
        formDisplays.shownFields(NodeEntityType.ID, base.bundle(), FormDisplayConfig.DEFAULT_MODE)
                .forEach(field -> fields.put(field, List.of()));
        fields.putAll(formDisplays.extract(
                NodeEntityType.ID, base.bundle(), FormDisplayConfig.DEFAULT_MODE, values));
        EntityData candidate = new EntityData(NodeEntityType.ID, base.id(), base.uuid(), base.bundle(),
                submitted.getOrDefault(TITLE, ""), base.langcode(), null, fields);
        Optional<Moderation> moderation = moderations.moderationOf(base.bundle());
        boolean newRevision = base.id() == null || moderation.isPresent() || submitted.containsKey(NEW_REVISION);
        String log = submitted.getOrDefault(REVISION_LOG, "");
        String chosenState = submitted.getOrDefault(MODERATION_STATE, "");
        String alias = submitted.getOrDefault(PATH, "").strip();
        boolean automatic = hasPattern(base) && FormRenderer.CHECKED_VALUE.equals(submitted.get(PATH_AUTO));
        FormElement tree = nodeForm(type, candidate, newRevision, log, stateElement(base, chosenState), alias,
                automatic);

        FormState state = forms.validate(tree, values);
        String source = (base.id() == null) ? null : NodeEntityType.path(base.id());
        if (!automatic && !alias.isEmpty()) {
            aliases.refusal(source, alias, base.langcode()).ifPresent(reason -> state.error(PATH, reason));
        }
        if (state.hasErrors()) {
            return formPage(type, title, action, tree, state.errors(), model);
        }
        EntityData saved;
        try {
            saved = moderation.isPresent()
                    ? moderations.save(moderation.get(), candidate, live, chosenState, CurrentAccount.id(), log,
                            SecurityContextHolder.getContext().getAuthentication())
                    : nodes.save(candidate, CurrentAccount.id(), newRevision, log);
        } catch (EntityValidationException invalid) {
            Map<String, String> errors = new LinkedHashMap<>();
            invalid.violations().forEach(violation -> errors.putIfAbsent(elementOf(violation), violation.message()));
            return formPage(type, title, action, tree, errors, model);
        } catch (ModerationRefusedException refused) {
            return formPage(type, title, action, tree, Map.of(MODERATION_STATE, refused.getMessage()), model);
        }
        saveAlias(saved, alias, automatic);
        return "redirect:" + NodeEntityType.path(saved.id());
    }

    private boolean hasPattern(EntityData node) {
        return patterns.find(NodeEntityType.ID, node.bundle()).isPresent();
    }

    /**
     * Gives the saved node's page the alias typed, or with automatic aliases,
     * one made from the content type's pattern: when it has none yet, when its
     * alias was typed, or when the pattern makes the alias again on every save.
     */
    private void saveAlias(EntityData saved, String typed, boolean automatic) {
        String source = NodeEntityType.path(saved.id());
        if (!automatic) {
            aliases.save(source, typed, saved.langcode());
            return;
        }
        boolean keep = aliases.generated(source, saved.langcode())
                && !patterns.find(NodeEntityType.ID, saved.bundle()).orElseThrow().regenerate();
        if (!keep) {
            patterns.generate(saved, source)
                    .ifPresent(made -> aliases.save(source, made, saved.langcode(), true));
        }
    }

    /**
     * For a moderated node, the state the save moves it into: one of the states
     * the person may move it into from the one it is in, starting on the given
     * one. Nothing for a node no workflow moderates.
     */
    private Optional<FormElement> stateElement(EntityData node, String chosen) {
        return moderations.moderationOf(node.bundle()).map(moderation -> FormElement
                .of(ElementType.SELECT, MODERATION_STATE)
                .label("Change to")
                .markRequired()
                .value(chosen)
                .options(moderations.reachableStates(moderation, ContentModerationService.stateOf(node),
                                SecurityContextHolder.getContext().getAuthentication()).stream()
                        .map(state -> new SelectOption(state.id(), state.label()))
                        .toList()));
    }

    /** The field a violation belongs to, which its path names after the field prefix. */
    private static String elementOf(ConstraintViolation violation) {
        return violation.propertyPath().substring(FieldConstraintProvider.FIELD_PATH_PREFIX.length());
    }

    /**
     * The title, the fields the form display shows, and the revision the save
     * makes. Saving a node that exists can write over its current revision
     * instead of making a new one, starting from what its type says, unless a
     * workflow moderates it, when every save is a new revision in the state the
     * form names.
     */
    private FormElement nodeForm(NodeType type, EntityData node, boolean newRevision, String log,
            Optional<FormElement> moderationState, String alias, boolean automatic) {
        FormElement pathSettings = FormElement.of(ElementType.DETAILS, "path-settings").label("URL alias");
        if (hasPattern(node)) {
            pathSettings.child(FormElement.of(ElementType.CHECKBOX, PATH_AUTO).label("Generate automatic URL alias")
                    .value(automatic));
        }
        pathSettings.child(FormElement.of(ElementType.TEXTFIELD, PATH).label("URL alias")
                .description("Another path the page answers at, such as /about. Blank for none.")
                .value(alias)
                .disabledWhen(PATH_AUTO, FormRenderer.CHECKED_VALUE)
                .rule(ValidationRule.maxLength(PathAliasManager.MAX_LENGTH))
                .rule(ValidationRule.pattern(PathAliasManager.PATTERN).withMessage(PathAliasManager.PATTERN_MESSAGE)));
        String cancel = (node.id() == null) ? ADD_PATH : NodeEntityType.path(node.id());
        FormElement revision = FormElement.of(ElementType.DETAILS, "revision-information").label("Revision information");
        if (node.id() != null && moderationState.isEmpty()) {
            revision.child(FormElement.of(ElementType.CHECKBOX, NEW_REVISION)
                    .label("Create new revision")
                    .value(newRevision));
        }
        moderationState.ifPresent(revision::child);
        revision.child(FormElement.of(ElementType.TEXTAREA, REVISION_LOG)
                .label("Revision log message")
                .description("Briefly describe the changes you have made.")
                .value(log)
                .rule(ValidationRule.maxLength(REVISION_LOG_MAX_LENGTH)));
        return FormElement.of(ElementType.CONTAINER, "node-form")
                .child(FormElement.of(ElementType.TEXTFIELD, TITLE)
                        .label(type.titleLabel())
                        .markRequired()
                        .value(node.label())
                        .rule(ValidationRule.maxLength(TITLE_MAX_LENGTH)))
                .child(formDisplays.buildContainer(
                        NodeEntityType.ID, node.bundle(), FormDisplayConfig.DEFAULT_MODE, node.fields()))
                .child(revision)
                .child(pathSettings)
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(cancel)));
    }

    private String formPage(NodeType type, String title, String action, FormElement tree,
            Map<String, String> errors, Model model) {
        model.addAttribute("title", title);
        model.addAttribute("description", type.help().isBlank() ? type.description() : type.help());
        model.addAttribute("action", action);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/block-form";
    }

    /**
     * View, then Latest version, Edit, Revisions, and Layout for someone who may
     * read a revision ahead of the live one, change the node, read its
     * revisions, and change its layout.
     */
    private List<Tab> tabs(EntityData node) {
        return tabs(node, NodeEntityType.path(node.id()));
    }

    private List<Tab> tabs(EntityData node, String activePath) {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(new Tab("View", NodeEntityType.path(node.id()), activePath.equals(NodeEntityType.path(node.id()))));
        if (revisionAccess.mayViewLatestVersion(node) && moderations.hasForwardRevision(node)) {
            tabs.add(new Tab("Latest version", latestPath(node.id()), activePath.equals(latestPath(node.id()))));
        }
        if (entityAccess.may(NodeEntityType.ID, node, EntityAccessHandler.UPDATE)) {
            tabs.add(new Tab("Edit", NodeEntityType.editPath(node.id()), false));
        }
        if (revisionAccess.mayView(node)) {
            tabs.add(new Tab("Revisions", NodeRevisionController.historyPath(node.id()), false));
        }
        if (layoutAccess.mayOverride(node)) {
            tabs.add(new Tab("Layout", NodeLayoutController.layoutPath(node.id()), false));
        }
        return tabs;
    }

    private NodeType creatableType(String type) {
        NodeType contentType = types.find(type)
                .orElseThrow(() -> new EntityNotFoundException("content type", type));
        if (!entityAccess.may(NodeEntityType.ID, type, EntityAccessHandler.CREATE)) {
            throw new AccessDeniedException("You may not create " + contentType.label() + " content.");
        }
        return contentType;
    }

    private EntityData permitted(long id, String operation) {
        EntityData node = nodes.find(id)
                .orElseThrow(() -> new EntityNotFoundException("node", String.valueOf(id)));
        if (!entityAccess.may(NodeEntityType.ID, node, operation)) {
            throw new AccessDeniedException("You may not " + operation + " this content.");
        }
        return node;
    }

    private NodeType typeOf(EntityData node) {
        return types.find(node.bundle())
                .orElseThrow(() -> new EntityNotFoundException("content type", node.bundle()));
    }
}
