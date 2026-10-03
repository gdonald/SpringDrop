package dev.springdrop.web;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockPageRenderer;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityValidationException;
import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.entity.query.EntityQuery;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.entity.query.Sort;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldConstraintProvider;
import dev.springdrop.kernel.field.display.FormDisplayConfig;
import dev.springdrop.kernel.field.display.FormDisplayManager;
import dev.springdrop.kernel.field.display.ViewDisplayConfig;
import dev.springdrop.kernel.field.display.ViewDisplayManager;
import dev.springdrop.kernel.field.types.EntityReferenceFieldType;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeListing;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.path.PathAliasManager;
import dev.springdrop.kernel.views.DefaultViews;
import dev.springdrop.kernel.views.ViewDisplay;
import dev.springdrop.kernel.views.ViewEmbed;
import dev.springdrop.kernel.views.ViewRenderer;
import dev.springdrop.kernel.render.RenderService;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.kernel.taxonomy.TaxonomyEntityType;
import dev.springdrop.kernel.taxonomy.TaxonomyService;
import dev.springdrop.kernel.taxonomy.TermHierarchyException;
import dev.springdrop.kernel.taxonomy.TermTreeItem;
import dev.springdrop.kernel.taxonomy.Vocabulary;
import dev.springdrop.kernel.taxonomy.VocabularyManager;
import dev.springdrop.kernel.text.MachineNameGenerator;
import dev.springdrop.kernel.theme.PageChrome;
import dev.springdrop.kernel.theme.Tab;
import dev.springdrop.kernel.validation.ConstraintViolation;
import dev.springdrop.kernel.web.EntityNotFoundException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

/**
 * Vocabularies and their terms: adding, changing, and deleting vocabularies,
 * arranging a vocabulary's terms into a tree, and writing, reading, and
 * deleting terms. Deleting a vocabulary deletes its terms, and deleting a term
 * deletes the terms below it.
 */
@Controller
public class TaxonomyController {

    public static final String PATH = "/admin/structure/taxonomy";

    public static final String NAME = "name";

    public static final String DESCRIPTION = "description";

    public static final String PARENT = "parent";

    public static final String WEIGHT = "weight";

    public static final String PUBLISHED = "published";

    /** The prefix of each term's parent choice on the overview, followed by the term's id. */
    public static final String PARENT_PREFIX = "parent_";

    /** The prefix of each term's weight input on the overview, followed by the term's id. */
    public static final String WEIGHT_PREFIX = "weight_";

    public static final String PAGE = "page";

    public static final int PER_PAGE = 10;

    static final String RSS_TYPE = "application/rss+xml";

    static final int NAME_MAX_LENGTH = 255;

    private static final String ROOT_LABEL = "<root>";

    private final VocabularyManager vocabularies;
    private final TaxonomyService taxonomy;
    private final EntityAccessManager entityAccess;
    private final FormDisplayManager formDisplays;
    private final ViewDisplayManager viewDisplays;
    private final MachineNameGenerator machineNames;
    private final FormBuilder forms;
    private final FormRenderer renderer;
    private final ConfigStore configStore;
    private final BlockPageRenderer pages;
    private final FieldConfigManager fields;
    private final EntityQueryExecutor queries;
    private final NodeListing listings;
    private final NodeService nodes;
    private final RenderService renderService;
    private final PathAliasManager aliases;
    private final ViewEmbed views;

    public TaxonomyController(
            VocabularyManager vocabularies,
            TaxonomyService taxonomy,
            EntityAccessManager entityAccess,
            FormDisplayManager formDisplays,
            ViewDisplayManager viewDisplays,
            MachineNameGenerator machineNames,
            FormBuilder forms,
            FormRenderer renderer,
            ConfigStore configStore,
            BlockPageRenderer pages,
            FieldConfigManager fields,
            EntityQueryExecutor queries,
            NodeListing listings,
            NodeService nodes,
            RenderService renderService,
            PathAliasManager aliases,
            ViewEmbed views) {
        this.vocabularies = vocabularies;
        this.taxonomy = taxonomy;
        this.entityAccess = entityAccess;
        this.formDisplays = formDisplays;
        this.viewDisplays = viewDisplays;
        this.machineNames = machineNames;
        this.forms = forms;
        this.renderer = renderer;
        this.configStore = configStore;
        this.pages = pages;
        this.fields = fields;
        this.queries = queries;
        this.listings = listings;
        this.nodes = nodes;
        this.renderService = renderService;
        this.aliases = aliases;
        this.views = views;
    }

    public static String managePath(String vocabulary) {
        return PATH + "/manage/" + vocabulary;
    }

    public static String overviewPath(String vocabulary) {
        return managePath(vocabulary) + "/overview";
    }

    @GetMapping(PATH)
    public String list(Model model) {
        model.addAttribute("title", "Taxonomy");
        model.addAttribute("description", "The vocabularies content is classified by, and the terms in each.");
        model.addAttribute("vocabularies", vocabularies.all());
        model.addAttribute("addPath", PATH + "/add");
        model.addAttribute("managePath", PATH + "/manage/");
        model.addAttribute("fieldsPath", FieldUiController.PATH_PREFIX + "/" + TaxonomyEntityType.ID + "/");
        return "admin/vocabularies";
    }

    @GetMapping(PATH + "/add")
    public String addVocabularyForm(Model model) {
        return formPage("Add vocabulary", PATH + "/add", vocabularyForm(Vocabulary.of("", "")), Map.of(), model);
    }

    @PostMapping(PATH + "/add")
    public String addVocabulary(@RequestParam Map<String, String> submitted, Model model) {
        String label = submitted.getOrDefault(NAME, "");
        String id = label.isBlank() ? "" : machineNames.generateUnique(label, taken -> vocabularies.find(taken)
                .isPresent());
        return saveVocabulary(new Vocabulary(id, label, submitted.getOrDefault(DESCRIPTION, ""), 0), submitted,
                "Add vocabulary", PATH + "/add", model);
    }

    @GetMapping(PATH + "/manage/{vocabulary}")
    public String editVocabularyForm(@PathVariable String vocabulary, Model model) {
        Vocabulary existing = vocabularyOf(vocabulary);
        return formPage("Edit " + existing.label(), managePath(vocabulary), vocabularyForm(existing), Map.of(),
                model);
    }

    @PostMapping(PATH + "/manage/{vocabulary}")
    public String editVocabulary(@PathVariable String vocabulary, @RequestParam Map<String, String> submitted,
            Model model) {
        Vocabulary existing = vocabularyOf(vocabulary);
        return saveVocabulary(new Vocabulary(existing.id(), submitted.getOrDefault(NAME, ""),
                        submitted.getOrDefault(DESCRIPTION, ""), existing.weight()),
                submitted, "Edit " + existing.label(), managePath(vocabulary), model);
    }

    @GetMapping(PATH + "/manage/{vocabulary}/delete")
    public String confirmDeleteVocabulary(@PathVariable String vocabulary, Model model) {
        Vocabulary existing = vocabularyOf(vocabulary);
        return confirmPage("Delete the vocabulary " + existing.label() + "?",
                "Every term in it is deleted too. This action cannot be undone.",
                managePath(vocabulary) + "/delete", "Delete vocabulary", PATH, model);
    }

    @PostMapping(PATH + "/manage/{vocabulary}/delete")
    public String deleteVocabulary(@PathVariable String vocabulary) {
        vocabularyOf(vocabulary);
        taxonomy.deleteTermsIn(vocabulary);
        vocabularies.delete(vocabulary);
        return "redirect:" + PATH;
    }

    /** The vocabulary's terms as a tree, each one's parent and weight ready to change. */
    @GetMapping(PATH + "/manage/{vocabulary}/overview")
    public String overview(@PathVariable String vocabulary, Model model) {
        Vocabulary existing = vocabularyOf(vocabulary);
        List<TermTreeItem> tree = taxonomy.tree(vocabulary);
        List<TermOverviewRow> rows = new ArrayList<>();
        for (TermTreeItem item : tree) {
            rows.add(new TermOverviewRow(item.id(), item.name(), item.depth(), item.parent(), item.weight(),
                    parentOptions(tree, taxonomy.descendantsOf(item.id()), item.id())));
        }
        model.addAttribute("title", existing.label());
        model.addAttribute("description", existing.description());
        model.addAttribute("rows", rows);
        model.addAttribute("action", overviewPath(vocabulary));
        model.addAttribute("addPath", managePath(vocabulary) + "/add");
        model.addAttribute("parentPrefix", PARENT_PREFIX);
        model.addAttribute("weightPrefix", WEIGHT_PREFIX);
        return "admin/term-overview";
    }

    /**
     * Saves the parent and weight of every term on the overview. A parent that
     * would break the tree, or a weight that is not a whole number, leaves that
     * part of the term as it was.
     */
    @PostMapping(PATH + "/manage/{vocabulary}/overview")
    public String saveOverview(@PathVariable String vocabulary, @RequestParam Map<String, String> submitted) {
        vocabularyOf(vocabulary);
        for (TermTreeItem item : taxonomy.tree(vocabulary)) {
            EntityData term = taxonomy.find(item.id()).orElseThrow();
            Map<String, Object> fields = new LinkedHashMap<>(term.fields());
            String weight = submitted.getOrDefault(WEIGHT_PREFIX + item.id(), "");
            if (weight.matches(BlockLayoutController.WHOLE_NUMBER)) {
                fields.put(TaxonomyEntityType.WEIGHT, Integer.parseInt(weight));
            }
            String parent = submitted.getOrDefault(PARENT_PREFIX + item.id(), "");
            if (parent.matches("\\d{1,18}")) {
                fields.put(TaxonomyEntityType.PARENT, Long.parseLong(parent));
            }
            try {
                taxonomy.save(term.withFields(fields));
            } catch (TermHierarchyException refused) {
                fields.put(TaxonomyEntityType.PARENT, item.parent());
                taxonomy.save(term.withFields(fields));
            }
        }
        return "redirect:" + overviewPath(vocabulary);
    }

    @GetMapping(PATH + "/manage/{vocabulary}/add")
    public String addTermForm(@PathVariable String vocabulary, Model model) {
        vocabularyOf(vocabulary);
        EntityData blank = taxonomy.create(vocabulary);
        return formPage("Add term", managePath(vocabulary) + "/add", termForm(blank, overviewPath(vocabulary)),
                Map.of(), model);
    }

    @PostMapping(PATH + "/manage/{vocabulary}/add")
    public String addTerm(@PathVariable String vocabulary, @RequestParam Map<String, String> submitted,
            Model model) {
        vocabularyOf(vocabulary);
        return saveTerm(taxonomy.create(vocabulary), submitted, "Add term", managePath(vocabulary) + "/add",
                overviewPath(vocabulary), model);
    }

    /**
     * A term with its description and fields, then the published content tagged
     * with it, drawn by the {@code taxonomy_term} view: teasers, a page at a time,
     * newest first after any sticky ones. The page points feed readers at the
     * term's feed.
     */
    @GetMapping(value = "/taxonomy/term/{id}", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String view(@PathVariable long id, @RequestParam(name = PAGE, defaultValue = "1") String requestedPage) {
        EntityData term = permitted(id, EntityAccessHandler.VIEW);
        String path = TaxonomyEntityType.path(id);
        SiteInformation site = configStore.read(
                SiteInformation.CONFIG_NAME, SiteInformation.class, SiteInformation.DEFAULTS);
        List<Tab> tabs = new ArrayList<>(List.of(new Tab("View", path, true)));
        if (entityAccess.may(TaxonomyEntityType.ID, term, EntityAccessHandler.UPDATE)) {
            tabs.add(new Tab("Edit", path + "/edit", false));
        }
        Renderable listing = Renderable.of("container").attribute("class", "term-content");
        Optional<ViewRenderer.Rendered> tagged = views.render(DefaultViews.TAXONOMY_TERM, ViewDisplay.DEFAULT,
                List.of(String.valueOf(id)), Map.of(PAGE, requestedPage), path, false);
        if (tagged.isPresent()) {
            listing = listing.child(Renderable.of("markup").with("value", tagged.get().html()));
        }
        Renderable content = Renderable.of("container").attribute("class", "taxonomy-term")
                .child(Renderable.of("text").attribute("class", "term-description")
                        .with("value", String.valueOf(term.fields().getOrDefault(TaxonomyEntityType.DESCRIPTION, ""))))
                .child(Renderable.of("markup").with("value", viewDisplays.render(TaxonomyEntityType.ID, term.bundle(),
                        ViewDisplayConfig.FULL_MODE, term.fields())))
                .child(listing)
                .child(Renderable.of("link").attribute("class", "term-feed")
                        .with("url", feedPath(id)).with("label", "Subscribe to " + term.label()))
                .headTag("<link rel=\"alternate\" type=\"application/rss+xml\" title=\""
                        + HtmlUtils.htmlEscape(term.label()) + "\" href=\"" + feedPath(id) + "\">")
                .cacheTag(TaxonomyService.cacheTag(id))
                .cacheTag(NodeService.LIST_CACHE_TAG);

        PageChrome chrome = PageChrome.of(site.name(), term.label()).withTabs(tabs);
        return pages.render(chrome, content, BlockContext.of(path, term.label()).withRouteEntity(term)).html();
    }

    /** The newest published content tagged with a term, as an RSS feed. */
    @GetMapping(value = "/taxonomy/term/{id}/feed", produces = RSS_TYPE)
    @ResponseBody
    public String feed(@PathVariable long id) {
        EntityData term = permitted(id, EntityAccessHandler.VIEW);
        SiteInformation site = configStore.read(
                SiteInformation.CONFIG_NAME, SiteInformation.class, SiteInformation.DEFAULTS);
        StringBuilder items = new StringBuilder();
        for (EntityData node : tagged(id).map(query -> listings.first(query, PER_PAGE)).orElse(List.of())) {
            String link = site.url() + aliases.outbound(NodeEntityType.path(node.id()));
            String teaser = renderService.render(nodes.build(node, ViewDisplayConfig.TEASER_MODE, false)).html();
            items.append("<item><title>").append(HtmlUtils.htmlEscape(node.label())).append("</title>")
                    .append("<link>").append(HtmlUtils.htmlEscape(link)).append("</link>")
                    .append("<description>").append(HtmlUtils.htmlEscape(teaser)).append("</description>")
                    .append(published(node))
                    .append("<guid isPermaLink=\"true\">").append(HtmlUtils.htmlEscape(link)).append("</guid></item>");
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss version=\"2.0\"><channel>"
                + "<title>" + HtmlUtils.htmlEscape(term.label()) + "</title>"
                + "<link>" + HtmlUtils.htmlEscape(site.url() + TaxonomyEntityType.path(id)) + "</link>"
                + "<description>" + HtmlUtils.htmlEscape(String.valueOf(
                        term.fields().getOrDefault(TaxonomyEntityType.DESCRIPTION, ""))) + "</description>"
                + items
                + "</channel></rss>";
    }

    public static String feedPath(Object id) {
        return TaxonomyEntityType.path(id) + "/feed";
    }

    private static String published(EntityData node) {
        return (node.fields().get(BaseFieldDefinition.CREATED) instanceof OffsetDateTime created)
                ? "<pubDate>" + DateTimeFormatter.RFC_1123_DATE_TIME.format(created) + "</pubDate>"
                : "";
    }

    /**
     * The published nodes tagged with the term through any of the node
     * reference fields that point at terms, sticky ones first and then the
     * newest, or nothing on a site with no such field.
     */
    private Optional<EntityQuery> tagged(long termId) {
        List<Condition> tags = fields.storages(NodeEntityType.ID).stream()
                .filter(storage -> storage.type().equals(EntityReferenceFieldType.ID)
                        && TaxonomyEntityType.ID.equals(EntityReferenceFieldType.targetType(storage)))
                .map(storage -> Condition.equal(storage.name(), termId))
                .toList();
        if (tags.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(queries.query(NodeEntityType.ID)
                .condition(Condition.equal(BaseFieldDefinition.STATUS, true))
                .condition(Condition.anyOf(tags.toArray(Condition[]::new)))
                .sort(Sort.descending(NodeEntityType.STICKY))
                .sort(Sort.descending(BaseFieldDefinition.CREATED))
                .sort(Sort.descending("id")));
    }

    @GetMapping("/taxonomy/term/{id}/edit")
    public String editTermForm(@PathVariable long id, Model model) {
        EntityData term = permitted(id, EntityAccessHandler.UPDATE);
        return formPage("Edit " + term.label(), TaxonomyEntityType.path(id) + "/edit",
                termForm(term, TaxonomyEntityType.path(id)), Map.of(), model);
    }

    @PostMapping("/taxonomy/term/{id}/edit")
    public String editTerm(@PathVariable long id, @RequestParam Map<String, String> submitted, Model model) {
        EntityData term = permitted(id, EntityAccessHandler.UPDATE);
        return saveTerm(term, submitted, "Edit " + term.label(), TaxonomyEntityType.path(id) + "/edit",
                TaxonomyEntityType.path(id), model);
    }

    @GetMapping("/taxonomy/term/{id}/delete")
    public String confirmDeleteTerm(@PathVariable long id, Model model) {
        EntityData term = permitted(id, EntityAccessHandler.DELETE);
        return confirmPage("Delete the term " + term.label() + "?",
                "Every term below it is deleted too. This action cannot be undone.",
                TaxonomyEntityType.path(id) + "/delete", "Delete term", TaxonomyEntityType.path(id), model);
    }

    @PostMapping("/taxonomy/term/{id}/delete")
    public String deleteTerm(@PathVariable long id) {
        EntityData term = permitted(id, EntityAccessHandler.DELETE);
        taxonomy.delete(id);
        return "redirect:" + overviewPath(term.bundle());
    }

    private String saveVocabulary(Vocabulary vocabulary, Map<String, String> submitted, String title,
            String action, Model model) {
        FormElement tree = vocabularyForm(vocabulary);
        FormState state = forms.validate(tree, new LinkedHashMap<>(submitted));
        if (state.hasErrors()) {
            return formPage(title, action, tree, state.errors(), model);
        }
        vocabularies.save(vocabulary);
        return "redirect:" + overviewPath(vocabulary.id());
    }

    /**
     * Saves the term once the form's rules, its fields' constraints, and the
     * tree all allow it, then shows where it went. Otherwise the form comes
     * back with each error on its element.
     */
    private String saveTerm(EntityData base, Map<String, String> submitted, String title, String action,
            String redirect, Model model) {
        Map<String, Object> values = new LinkedHashMap<>(submitted);
        Map<String, Object> fields = new LinkedHashMap<>(base.fields());
        formDisplays.shownFields(TaxonomyEntityType.ID, base.bundle(), FormDisplayConfig.DEFAULT_MODE)
                .forEach(field -> fields.put(field, List.of()));
        fields.putAll(formDisplays.extract(TaxonomyEntityType.ID, base.bundle(), FormDisplayConfig.DEFAULT_MODE,
                values));
        fields.put(TaxonomyEntityType.DESCRIPTION, submitted.getOrDefault(DESCRIPTION, ""));
        fields.put(BaseFieldDefinition.STATUS, submitted.containsKey(PUBLISHED));
        String weight = submitted.getOrDefault(WEIGHT, "");
        fields.put(TaxonomyEntityType.WEIGHT, weight.matches(BlockLayoutController.WHOLE_NUMBER)
                ? Integer.parseInt(weight) : 0);
        String parent = submitted.getOrDefault(PARENT, "");
        fields.put(TaxonomyEntityType.PARENT, parent.matches("\\d{1,18}")
                ? Long.parseLong(parent) : TaxonomyEntityType.ROOT);
        EntityData candidate = new EntityData(TaxonomyEntityType.ID, base.id(), base.uuid(), base.bundle(),
                submitted.getOrDefault(NAME, ""), base.langcode(), null, fields);
        FormElement tree = termForm(candidate, redirect);

        FormState state = forms.validate(tree, values);
        if (state.hasErrors()) {
            return formPage(title, action, tree, state.errors(), model);
        }
        try {
            taxonomy.save(candidate);
        } catch (EntityValidationException invalid) {
            Map<String, String> errors = new LinkedHashMap<>();
            invalid.violations().forEach(violation -> errors.putIfAbsent(elementOf(violation), violation.message()));
            return formPage(title, action, tree, errors, model);
        } catch (TermHierarchyException refused) {
            return formPage(title, action, tree, Map.of(PARENT, refused.getMessage()), model);
        }
        return "redirect:" + redirect;
    }

    private static String elementOf(ConstraintViolation violation) {
        return violation.propertyPath().substring(FieldConstraintProvider.FIELD_PATH_PREFIX.length());
    }

    private static FormElement vocabularyForm(Vocabulary vocabulary) {
        return FormElement.of(ElementType.CONTAINER, "vocabulary")
                .child(FormElement.of(ElementType.TEXTFIELD, NAME)
                        .label("Name")
                        .markRequired()
                        .value(vocabulary.label())
                        .rule(ValidationRule.maxLength(NAME_MAX_LENGTH)))
                .child(FormElement.of(ElementType.TEXTAREA, DESCRIPTION)
                        .label("Description")
                        .value(vocabulary.description())
                        .rule(ValidationRule.maxLength(NodeTypeController.TEXT_MAX_LENGTH)))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save vocabulary"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH)));
    }

    /**
     * The term's name, description, place in the tree, and fields. Its parent
     * is chosen from the top of the tree and the terms that are neither the term
     * itself nor below it.
     */
    private FormElement termForm(EntityData term, String cancelPath) {
        List<TermTreeItem> tree = taxonomy.tree(term.bundle());
        Set<Long> excluded = (term.id() == null) ? Set.of() : taxonomy.descendantsOf(((Number) term.id()).longValue());
        long self = (term.id() == null) ? -1L : ((Number) term.id()).longValue();
        return FormElement.of(ElementType.CONTAINER, "term")
                .child(FormElement.of(ElementType.TEXTFIELD, NAME)
                        .label("Name")
                        .markRequired()
                        .value(term.label())
                        .rule(ValidationRule.maxLength(NAME_MAX_LENGTH)))
                .child(FormElement.of(ElementType.TEXTAREA, DESCRIPTION)
                        .label("Description")
                        .value(term.fields().getOrDefault(TaxonomyEntityType.DESCRIPTION, ""))
                        .rule(ValidationRule.maxLength(NodeTypeController.TEXT_MAX_LENGTH)))
                .child(formDisplays.buildContainer(TaxonomyEntityType.ID, term.bundle(),
                        FormDisplayConfig.DEFAULT_MODE, term.fields()))
                .child(FormElement.of(ElementType.DETAILS, "relations").label("Relations")
                        .child(FormElement.of(ElementType.SELECT, PARENT)
                                .label("Parent term")
                                .markRequired()
                                .value(String.valueOf(TaxonomyService.parentOf(term)))
                                .options(parentOptions(tree, excluded, self)))
                        .child(FormElement.of(ElementType.NUMBER, WEIGHT)
                                .label("Weight")
                                .description("Lighter terms come first among their siblings.")
                                .value(TaxonomyService.weightOf(term))
                                .rule(ValidationRule.pattern(BlockLayoutController.WHOLE_NUMBER)
                                        .withMessage("The weight is a whole number."))))
                .child(FormElement.of(ElementType.CHECKBOX, PUBLISHED)
                        .label("Published")
                        .value(Boolean.TRUE.equals(term.fields().get(BaseFieldDefinition.STATUS))))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save term"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(cancelPath)));
    }

    /** The top of the tree, then each term the given one may sit under, indented by depth. */
    private static List<SelectOption> parentOptions(List<TermTreeItem> tree, Set<Long> excluded, long self) {
        List<SelectOption> options = new ArrayList<>();
        options.add(new SelectOption(String.valueOf(TaxonomyEntityType.ROOT), ROOT_LABEL));
        for (TermTreeItem item : tree) {
            if (item.id() != self && !excluded.contains(item.id())) {
                options.add(new SelectOption(String.valueOf(item.id()), "-".repeat(item.depth()) + item.name()));
            }
        }
        return options;
    }

    private String formPage(
            String title, String action, FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", title);
        model.addAttribute("description", "The vocabularies content is classified by, and the terms in each.");
        model.addAttribute("action", action);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/block-form";
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

    private Vocabulary vocabularyOf(String vocabulary) {
        return vocabularies.find(vocabulary).orElseThrow(() -> new EntityNotFoundException("vocabulary", vocabulary));
    }

    private EntityData permitted(long id, String operation) {
        EntityData term = taxonomy.find(id)
                .orElseThrow(() -> new EntityNotFoundException("term", String.valueOf(id)));
        if (!entityAccess.may(TaxonomyEntityType.ID, term, operation)) {
            throw new AccessDeniedException("You may not " + operation + " this term.");
        }
        return term;
    }
}
