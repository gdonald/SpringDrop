package dev.springdrop.web;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityKind;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.path.PathAliasManager;
import dev.springdrop.kernel.plugin.PluginManager;
import dev.springdrop.kernel.plugin.PluginRegistry;
import dev.springdrop.kernel.text.MachineNameGenerator;
import dev.springdrop.kernel.views.HandlerConfig;
import dev.springdrop.kernel.views.PluginConfig;
import dev.springdrop.kernel.views.RelationshipHandler;
import dev.springdrop.kernel.views.ViewConfig;
import dev.springdrop.kernel.views.ViewDisplay;
import dev.springdrop.kernel.views.ViewEditing;
import dev.springdrop.kernel.views.ViewManager;
import dev.springdrop.kernel.views.ViewOptions;
import dev.springdrop.kernel.views.ViewPaths;
import dev.springdrop.kernel.views.ViewPlugin;
import dev.springdrop.kernel.views.ViewRenderer;
import dev.springdrop.kernel.views.handlers.EntityLabelField;
import dev.springdrop.kernel.views.handlers.FullPager;
import dev.springdrop.kernel.views.handlers.NoneAccess;
import dev.springdrop.kernel.views.rows.FieldsRow;
import dev.springdrop.kernel.views.styles.UnformattedStyle;
import dev.springdrop.kernel.web.EntityNotFoundException;
import java.util.ArrayList;
import java.util.Comparator;
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
 * The Views UI: listing views, adding one, and editing its displays a part at
 * a time. A display that does not override a part edits the default display's,
 * shared by every display that does not override it either, and a display can
 * take a copy of a part to change on its own or give it back.
 */
@Controller
public class ViewsUiController {

    public static final String PATH = "/admin/structure/views";

    public static final String ADMINISTER_VIEWS = "administer views";

    public static final String LABEL = "label";

    public static final String DESCRIPTION = "description";

    public static final String BASE = "base";

    public static final String PLUGIN = "plugin";

    public static final String TARGET = "target";

    public static final String TITLE = "title";

    /** Prefixes the names of a plugin's own settings. */
    public static final String SETTINGS_PREFIX = "settings_";

    /** What a display's path is written as: parts of path characters or {@code %}, each after a slash. */
    public static final String DISPLAY_PATH_PATTERN = "(/([A-Za-z0-9_.~-]+|%))+";

    private static final Map<String, String> SECTION_LABELS = Map.of(
            ViewEditing.FIELDS, "Fields", ViewEditing.FILTERS, "Filters", ViewEditing.SORTS, "Sorts",
            ViewEditing.ARGUMENTS, "Contextual filters", ViewEditing.RELATIONSHIPS, "Relationships",
            ViewEditing.PAGER, "Pager", ViewEditing.STYLE, "Style", ViewEditing.ROW, "Row",
            ViewEditing.ACCESS, "Access");

    private final ViewManager views;
    private final ViewRenderer renderer;
    private final EntityTypeManager entityTypes;
    private final FieldConfigManager fields;
    private final PluginRegistry registry;
    private final PathAliasManager paths;
    private final MachineNameGenerator machineNames;
    private final FormBuilder formBuilder;
    private final FormRenderer forms;

    public ViewsUiController(ViewManager views, ViewRenderer renderer, EntityTypeManager entityTypes,
            FieldConfigManager fields, PluginRegistry registry, PathAliasManager paths,
            MachineNameGenerator machineNames, FormBuilder formBuilder, FormRenderer forms) {
        this.views = views;
        this.renderer = renderer;
        this.entityTypes = entityTypes;
        this.fields = fields;
        this.registry = registry;
        this.paths = paths;
        this.machineNames = machineNames;
        this.formBuilder = formBuilder;
        this.forms = forms;
    }

    public static String editPath(String viewId) {
        return PATH + "/view/" + viewId;
    }

    public static String displayPath(String viewId, String displayId) {
        return editPath(viewId) + "/display/" + displayId;
    }

    public record ViewRow(String id, String label, String description, String base, String displays) {
    }

    @GetMapping(PATH)
    public String list(Model model) {
        model.addAttribute("title", "Views");
        model.addAttribute("description", "The site's listings, each a query shown through one or more displays.");
        model.addAttribute("rows", views.all().stream().map(view -> new ViewRow(view.id(), view.label(),
                view.description(), view.baseEntityType(), String.join(", ", view.displays().stream()
                        .filter(display -> !display.id().equals(ViewDisplay.DEFAULT))
                        .map(this::displaySummary).toList()))).toList());
        model.addAttribute("addPath", PATH + "/add");
        model.addAttribute("editPath", PATH + "/view/");
        return "admin/views";
    }

    private String displaySummary(ViewDisplay display) {
        String path = display.text(ViewDisplay.PATH);
        String name = display.title().isEmpty() ? display.id() : display.title();
        return path.isEmpty() ? name + " (" + display.plugin() + ")" : name + " (" + path + ")";
    }

    @GetMapping(PATH + "/add")
    public String addForm(Model model) {
        return formPage("Add view", PATH + "/add", addingForm("", "", "", ""), Map.of(), model);
    }

    @PostMapping(PATH + "/add")
    public String add(@RequestParam Map<String, String> submitted, Model model) {
        String label = submitted.getOrDefault(LABEL, "").strip();
        String base = submitted.getOrDefault(BASE, "");
        String path = submitted.getOrDefault(ViewDisplay.PATH, "").strip();
        FormElement tree = addingForm(label, submitted.getOrDefault(DESCRIPTION, ""), base, path);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        if (!base.isEmpty() && !baseTypes().contains(base)) {
            state.error(BASE, "Choose what the view lists.");
        }
        pathRefusal(path).ifPresent(reason -> state.error(ViewDisplay.PATH, reason));
        if (state.hasErrors()) {
            return formPage("Add view", PATH + "/add", tree, state.errors(), model);
        }
        String id = machineNames.generateUnique(label, taken -> views.find(taken).isPresent());
        ViewOptions options = new ViewOptions(
                List.of(HandlerConfig.of("label", EntityLabelField.ID, "label", Map.of())),
                List.of(), List.of(), List.of(), List.of(), PluginConfig.of(FullPager.ID),
                PluginConfig.of(UnformattedStyle.ID), PluginConfig.of(FieldsRow.ID), PluginConfig.of(NoneAccess.ID));
        List<ViewDisplay> displays = new ArrayList<>(List.of(new ViewDisplay(ViewDisplay.DEFAULT,
                ViewDisplay.DEFAULT, label, options, Map.of())));
        if (!path.isEmpty()) {
            displays.add(new ViewDisplay("page_1", ViewDisplay.PAGE, label, ViewOptions.INHERIT,
                    Map.of(ViewDisplay.PATH, path)));
        }
        views.save(new ViewConfig(id, label, submitted.getOrDefault(DESCRIPTION, "").strip(), base, displays));
        return "redirect:" + editPath(id);
    }

    private FormElement addingForm(String label, String description, String base, String path) {
        return FormElement.of(ElementType.CONTAINER, "view")
                .child(FormElement.of(ElementType.TEXTFIELD, LABEL).label("Name").markRequired().value(label)
                        .rule(ValidationRule.maxLength(255)))
                .child(FormElement.of(ElementType.TEXTAREA, DESCRIPTION).label("Description").value(description)
                        .rule(ValidationRule.maxLength(1000)))
                .child(FormElement.of(ElementType.SELECT, BASE).label("Show").markRequired().value(base)
                        .options(baseTypes().stream().map(type -> new SelectOption(type, type)).toList()))
                .child(pathElement(path, "Leave blank to add a page later."))
                .child(actions("Save and edit", PATH));
    }

    /** The entity types a view can list: content entity types. */
    private List<String> baseTypes() {
        return entityTypes.all().stream().filter(type -> type.kind() == EntityKind.CONTENT).map(EntityType::id)
                .sorted().toList();
    }

    private static FormElement pathElement(String path, String description) {
        return FormElement.of(ElementType.TEXTFIELD, ViewDisplay.PATH).label("Path").value(path)
                .description("Such as /news or /news/%, where % takes a contextual filter's value. " + description)
                .rule(ValidationRule.maxLength(255))
                .rule(ValidationRule.pattern(DISPLAY_PATH_PATTERN).withMessage(
                        "Write the path as /news or /news/%, with letters, digits, and _ . ~ - between slashes."));
    }

    /** Why a display may not answer at a path: the site serves it already. */
    private Optional<String> pathRefusal(String path) {
        return !path.isEmpty() && path.matches(DISPLAY_PATH_PATTERN) && paths.served(path.replace("%", "0"))
                ? Optional.of("The path " + path + " is already a page of the site.") : Optional.empty();
    }

    public record Part(String key, String label, boolean own, String togglePath, String toggleLabel,
            List<PartItem> items, String addPath) {
    }

    public record PartItem(String summary, String editPath, String removePath) {
    }

    public record DisplayTab(String label, String path, boolean active) {
    }

    @GetMapping(PATH + "/view/{id}")
    public String edit(@PathVariable String id, @RequestParam(name = "display", defaultValue = ViewDisplay.DEFAULT)
            String displayId, Model model) {
        ViewConfig view = viewOf(id);
        ViewDisplay display = view.display(displayId).orElseThrow(() -> new EntityNotFoundException("view display",
                displayId));
        return editPage(view, display, displaySettingsForm(view, display), Map.of(), model);
    }

    private String editPage(ViewConfig view, ViewDisplay display, FormElement settings, Map<String, String> errors,
            Model model) {
        String base = displayPath(view.id(), display.id());
        model.addAttribute("title", "Edit view " + view.label());
        model.addAttribute("description", view.description());
        model.addAttribute("tabs", view.displays().stream().map(each -> new DisplayTab(
                each.id().equals(ViewDisplay.DEFAULT) ? "Default" : (each.title().isEmpty() ? each.id() : each.title()),
                editPath(view.id()) + "?display=" + each.id(), each.id().equals(display.id()))).toList());
        model.addAttribute("settingsAction", base + "/settings");
        model.addAttribute("settingsMarkup", forms.render(settings, errors));
        List<Part> parts = new ArrayList<>();
        ViewOptions options = view.options(display.id());
        for (String section : ViewEditing.SECTIONS) {
            List<HandlerConfig> handlers = ViewEditing.section(options, section);
            parts.add(part(view, display, section, (handlers == null ? List.<HandlerConfig>of() : handlers).stream()
                    .map(handler -> new PartItem(handlerSummary(section, handler),
                            base + "/" + section + "/" + handler.id(), base + "/" + section + "/" + handler.id()
                                    + "/remove")).toList(), base + "/" + section + "/add"));
        }
        for (String kind : ViewEditing.KINDS) {
            PluginConfig plugin = ViewEditing.plugin(options, kind);
            parts.add(part(view, display, kind, List.of(new PartItem(pluginSummary(kind, plugin),
                    base + "/plugin/" + kind, "")), ""));
        }
        model.addAttribute("parts", parts);
        model.addAttribute("addDisplayAction", editPath(view.id()) + "/displays/add");
        model.addAttribute("deleteDisplayAction", display.id().equals(ViewDisplay.DEFAULT) ? "" : base + "/delete");
        model.addAttribute("deletePath", editPath(view.id()) + "/delete");
        model.addAttribute("listPath", PATH);
        String path = display.text(ViewDisplay.PATH);
        model.addAttribute("preview", renderer.render(view, display.id(), List.of(), Map.of(),
                path.isEmpty() ? editPath(view.id()) : ViewPaths.address(display), true).html());
        return "admin/view-edit";
    }

    private Part part(ViewConfig view, ViewDisplay display, String key, List<PartItem> items, String addPath) {
        boolean isDefault = display.id().equals(ViewDisplay.DEFAULT);
        boolean own = ViewEditing.overrides(view, display.id(), key);
        return new Part(key, SECTION_LABELS.get(key), own,
                isDefault ? "" : displayPath(view.id(), display.id()) + "/override/" + key,
                own ? "Use the default display's" : "Change for this display only", items, addPath);
    }

    private String handlerSummary(String section, HandlerConfig handler) {
        PluginManager<? extends ViewPlugin> plugins = registry.managerFor(ViewEditing.PLUGIN_TYPES.get(section));
        String plugin = plugins.has(handler.plugin()) ? plugins.get(handler.plugin()).label()
                : "Missing " + handler.plugin();
        String through = handler.relationship().equals(HandlerConfig.BASE) ? "" : handler.relationship() + ": ";
        return plugin + " " + through + handler.property();
    }

    private String pluginSummary(String kind, PluginConfig plugin) {
        if (plugin == null) {
            return "None";
        }
        PluginManager<? extends ViewPlugin> plugins = registry.managerFor(ViewEditing.PLUGIN_TYPES.get(kind));
        return plugins.has(plugin.plugin()) ? plugins.get(plugin.plugin()).label() : "Missing " + plugin.plugin();
    }

    private FormElement displaySettingsForm(ViewConfig view, ViewDisplay display) {
        return displaySettingsForm(view, display, display.title(), display.settings());
    }

    private FormElement displaySettingsForm(ViewConfig view, ViewDisplay display, String title,
            Map<String, Object> settings) {
        FormElement form = FormElement.of(ElementType.CONTAINER, "display-settings")
                .child(FormElement.of(ElementType.TEXTFIELD, TITLE).label("Title").value(title)
                        .rule(ValidationRule.maxLength(255)));
        String plugin = display.plugin();
        if (plugin.equals(ViewDisplay.PAGE) || plugin.equals(ViewDisplay.FEED)) {
            form.child(pathElement(String.valueOf(settings.getOrDefault(ViewDisplay.PATH, "")), "").markRequired());
        }
        if (plugin.equals(ViewDisplay.PAGE)) {
            form.child(FormElement.of(ElementType.TEXTFIELD, ViewDisplay.MENU_TITLE).label("Menu link title")
                            .description("Blank for no menu link.")
                            .value(String.valueOf(settings.getOrDefault(ViewDisplay.MENU_TITLE, "")))
                            .rule(ValidationRule.maxLength(255)))
                    .child(FormElement.of(ElementType.TEXTFIELD, ViewDisplay.MENU).label("Menu")
                            .description("The menu's id, the main menu when blank.")
                            .value(String.valueOf(settings.getOrDefault(ViewDisplay.MENU, "")))
                            .rule(ValidationRule.pattern("[a-z0-9_-]{0,64}").withMessage("Use the menu's id.")))
                    .child(FormElement.of(ElementType.CHECKBOX, ViewDisplay.EXPOSED_BLOCK)
                            .label("Draw the exposed form in a block of its own")
                            .value(Boolean.TRUE.equals(settings.get(ViewDisplay.EXPOSED_BLOCK))));
        }
        if (plugin.equals(ViewDisplay.DEFAULT)) {
            form.child(FormElement.of(ElementType.CHECKBOX, ViewDisplay.ENTITY_ACCESS)
                    .label("Check each result's own access")
                    .value(!Boolean.FALSE.equals(settings.get(ViewDisplay.ENTITY_ACCESS))));
        }
        return form.child(FormElement.of(ElementType.TEXTFIELD, ViewRenderer.EMPTY_TEXT).label("Text with no results")
                        .value(String.valueOf(settings.getOrDefault(ViewRenderer.EMPTY_TEXT, "")))
                        .rule(ValidationRule.maxLength(1000)))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save display")));
    }

    @PostMapping(PATH + "/view/{id}/display/{display}/settings")
    public String saveSettings(@PathVariable String id, @PathVariable String display,
            @RequestParam Map<String, String> submitted, Model model) {
        ViewConfig view = viewOf(id);
        ViewDisplay shown = displayOf(view, display);
        Map<String, Object> settings = new LinkedHashMap<>(shown.settings());
        String title = submitted.getOrDefault(TITLE, "").strip();
        String path = submitted.getOrDefault(ViewDisplay.PATH, "").strip();
        String plugin = shown.plugin();
        if (plugin.equals(ViewDisplay.PAGE) || plugin.equals(ViewDisplay.FEED)) {
            settings.put(ViewDisplay.PATH, path);
        }
        if (plugin.equals(ViewDisplay.PAGE)) {
            settings.put(ViewDisplay.MENU_TITLE, submitted.getOrDefault(ViewDisplay.MENU_TITLE, "").strip());
            settings.put(ViewDisplay.MENU, submitted.getOrDefault(ViewDisplay.MENU, "").strip());
            settings.put(ViewDisplay.EXPOSED_BLOCK, submitted.containsKey(ViewDisplay.EXPOSED_BLOCK));
        }
        if (plugin.equals(ViewDisplay.DEFAULT)) {
            settings.put(ViewDisplay.ENTITY_ACCESS, submitted.containsKey(ViewDisplay.ENTITY_ACCESS));
        }
        settings.put(ViewRenderer.EMPTY_TEXT, submitted.getOrDefault(ViewRenderer.EMPTY_TEXT, "").strip());
        FormElement tree = displaySettingsForm(view, shown, title, settings);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        if (!state.hasErrors()) {
            pathRefusal(path).ifPresent(reason -> state.error(ViewDisplay.PATH, reason));
        }
        if (state.hasErrors()) {
            return editPage(view, shown, tree, state.errors(), model);
        }
        views.save(view.withDisplay(new ViewDisplay(shown.id(), plugin, title, shown.overrides(), settings)));
        return "redirect:" + editPath(id) + "?display=" + display;
    }

    @PostMapping(PATH + "/view/{id}/display/{display}/override/{part}")
    public String toggleOverride(@PathVariable String id, @PathVariable String display, @PathVariable String part) {
        ViewConfig view = viewOf(id);
        displayOf(view, display);
        if ((ViewEditing.SECTIONS.contains(part) || ViewEditing.KINDS.contains(part))
                && !display.equals(ViewDisplay.DEFAULT)) {
            views.save(ViewEditing.toggleOverride(view, display, part));
        }
        return "redirect:" + editPath(id) + "?display=" + display;
    }

    @GetMapping(PATH + "/view/{id}/display/{display}/{section}/add")
    public String addHandlerForm(@PathVariable String id, @PathVariable String display, @PathVariable String section,
            Model model) {
        ViewConfig view = viewOf(id);
        displayOf(view, display);
        sectionOf(section);
        return formPage("Add to " + SECTION_LABELS.get(section), displayPath(id, display) + "/" + section + "/add",
                addingHandlerForm(view, display, section, "", ""), Map.of(), model);
    }

    @PostMapping(PATH + "/view/{id}/display/{display}/{section}/add")
    public String addHandler(@PathVariable String id, @PathVariable String display, @PathVariable String section,
            @RequestParam Map<String, String> submitted, Model model) {
        ViewConfig view = viewOf(id);
        displayOf(view, display);
        sectionOf(section);
        String plugin = submitted.getOrDefault(PLUGIN, "");
        String target = submitted.getOrDefault(TARGET, "");
        FormElement tree = addingHandlerForm(view, display, section, plugin, target);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        if (!state.hasErrors()) {
            if (!registry.managerFor(ViewEditing.PLUGIN_TYPES.get(section)).has(plugin)) {
                state.error(PLUGIN, "Choose what handles it.");
            }
            if (!targets(view, display, section).contains(target)) {
                state.error(TARGET, "Choose a property.");
            }
        }
        if (state.hasErrors()) {
            return formPage("Add to " + SECTION_LABELS.get(section), displayPath(id, display) + "/" + section
                    + "/add", tree, state.errors(), model);
        }
        String[] parts = target.split("\\|", 2);
        List<HandlerConfig> existing = Optional.ofNullable(ViewEditing.section(view.options(display), section))
                .orElse(List.of());
        String handlerId = machineNames.generateUnique(parts[1], taken -> taken.equals("add")
                || existing.stream().anyMatch(each -> each.id().equals(taken)));
        HandlerConfig handler = new HandlerConfig(handlerId, plugin, parts[0], parts[1], Map.of());
        views.save(ViewEditing.editSection(view, display, section, current -> {
            List<HandlerConfig> changed = new ArrayList<>(current);
            changed.add(handler);
            return changed;
        }));
        return "redirect:" + displayPath(id, display) + "/" + section + "/" + handlerId;
    }

    private FormElement addingHandlerForm(ViewConfig view, String display, String section, String plugin,
            String target) {
        PluginManager<? extends ViewPlugin> plugins = registry.managerFor(ViewEditing.PLUGIN_TYPES.get(section));
        return FormElement.of(ElementType.CONTAINER, "handler")
                .child(FormElement.of(ElementType.SELECT, PLUGIN).label("Handled by").markRequired().value(plugin)
                        .options(plugins.ids().stream().map(each -> new SelectOption(each, plugins.get(each).label()))
                                .sorted(Comparator.comparing(SelectOption::label)).toList()))
                .child(FormElement.of(ElementType.SELECT, TARGET).label("Property").markRequired().value(target)
                        .options(targets(view, display, section).stream().map(each -> new SelectOption(each,
                                each.startsWith(HandlerConfig.BASE + "|") ? each.substring(HandlerConfig.BASE.length()
                                        + 1) : each.replace("|", ": "))).toList()))
                .child(actions("Add", editPath(view.id()) + "?display=" + display));
    }

    /**
     * The properties a handler can read, written {@code <relationship>|<property>}:
     * the base entity's, and for any section but relationships, those of each
     * relationship's entity.
     */
    private List<String> targets(ViewConfig view, String display, String section) {
        List<String> targets = new ArrayList<>();
        properties(view.baseEntityType()).forEach(property -> targets.add(HandlerConfig.BASE + "|" + property));
        if (!section.equals(ViewEditing.RELATIONSHIPS)) {
            PluginManager<RelationshipHandler> relationships = registry.managerFor(RelationshipHandler.class);
            List<HandlerConfig> configured = view.options(display).relationships();
            for (HandlerConfig relationship : configured == null ? List.<HandlerConfig>of() : configured) {
                if (relationships.has(relationship.plugin())) {
                    String targetType = relationships.get(relationship.plugin())
                            .targetType(view.baseEntityType(), relationship);
                    properties(targetType).forEach(property -> targets.add(relationship.id() + "|" + property));
                }
            }
        }
        return targets;
    }

    /** An entity type's properties: its id, label, bundle, base fields, and configured fields. */
    private List<String> properties(String entityTypeId) {
        List<String> properties = new ArrayList<>(List.of("id", "label"));
        entityTypes.find(entityTypeId).ifPresent(type -> {
            if (type.keys().bundle() != null) {
                properties.add(type.keys().bundle());
            }
            type.baseFields().stream().map(BaseFieldDefinition::name).forEach(properties::add);
        });
        fields.storages(entityTypeId).stream().map(FieldStorageConfig::name).forEach(properties::add);
        return properties.stream().distinct().toList();
    }

    @GetMapping(PATH + "/view/{id}/display/{display}/{section}/{handler}")
    public String handlerForm(@PathVariable String id, @PathVariable String display, @PathVariable String section,
            @PathVariable String handler, Model model) {
        ViewConfig view = viewOf(id);
        displayOf(view, display);
        HandlerConfig config = handlerOf(view, display, sectionOf(section), handler);
        return formPage("Configure " + handlerSummary(section, config), displayPath(id, display) + "/" + section
                + "/" + handler, handlerForm(view, display, section, config.settings(), config), Map.of(), model);
    }

    @PostMapping(PATH + "/view/{id}/display/{display}/{section}/{handler}")
    public String saveHandler(@PathVariable String id, @PathVariable String display, @PathVariable String section,
            @PathVariable String handler, @RequestParam Map<String, String> submitted, Model model) {
        ViewConfig view = viewOf(id);
        displayOf(view, display);
        HandlerConfig config = handlerOf(view, display, sectionOf(section), handler);
        PluginManager<? extends ViewPlugin> plugins = registry.managerFor(ViewEditing.PLUGIN_TYPES.get(section));
        Map<String, Object> settings = new LinkedHashMap<>(config.settings());
        if (plugins.has(config.plugin())) {
            settings.putAll(plugins.get(config.plugin()).settingsValues(SETTINGS_PREFIX, submitted));
        }
        FormElement tree = handlerForm(view, display, section, settings, config);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        if (state.hasErrors()) {
            return formPage("Configure " + handlerSummary(section, config), displayPath(id, display) + "/" + section
                    + "/" + handler, tree, state.errors(), model);
        }
        HandlerConfig changed = new HandlerConfig(config.id(), config.plugin(), config.relationship(),
                config.property(), settings);
        views.save(ViewEditing.editSection(view, display, section, current -> current.stream()
                .map(each -> each.id().equals(handler) ? changed : each).toList()));
        return "redirect:" + editPath(id) + "?display=" + display;
    }

    private FormElement handlerForm(ViewConfig view, String display, String section, Map<String, Object> settings,
            HandlerConfig config) {
        PluginManager<? extends ViewPlugin> plugins = registry.managerFor(ViewEditing.PLUGIN_TYPES.get(section));
        FormElement form = FormElement.of(ElementType.CONTAINER, "handler-settings");
        if (plugins.has(config.plugin())) {
            plugins.get(config.plugin()).settingsForm(SETTINGS_PREFIX, settings).forEach(form::child);
        }
        return form.child(actions("Apply", editPath(view.id()) + "?display=" + display));
    }

    @PostMapping(PATH + "/view/{id}/display/{display}/{section}/{handler}/remove")
    public String removeHandler(@PathVariable String id, @PathVariable String display, @PathVariable String section,
            @PathVariable String handler) {
        ViewConfig view = viewOf(id);
        displayOf(view, display);
        handlerOf(view, display, sectionOf(section), handler);
        views.save(ViewEditing.editSection(view, display, section, current -> current.stream()
                .filter(each -> !each.id().equals(handler)).toList()));
        return "redirect:" + editPath(id) + "?display=" + display;
    }

    @GetMapping(PATH + "/view/{id}/display/{display}/plugin/{kind}")
    public String pluginForm(@PathVariable String id, @PathVariable String display, @PathVariable String kind,
            Model model) {
        ViewConfig view = viewOf(id);
        displayOf(view, display);
        kindOf(kind);
        PluginConfig current = Optional.ofNullable(ViewEditing.plugin(view.options(display), kind))
                .orElse(PluginConfig.of(""));
        return formPage("Configure " + SECTION_LABELS.get(kind), displayPath(id, display) + "/plugin/" + kind,
                pluginForm(view, display, kind, current), Map.of(), model);
    }

    @PostMapping(PATH + "/view/{id}/display/{display}/plugin/{kind}")
    public String savePlugin(@PathVariable String id, @PathVariable String display, @PathVariable String kind,
            @RequestParam Map<String, String> submitted, Model model) {
        ViewConfig view = viewOf(id);
        displayOf(view, display);
        kindOf(kind);
        PluginManager<? extends ViewPlugin> plugins = registry.managerFor(ViewEditing.PLUGIN_TYPES.get(kind));
        String chosen = submitted.getOrDefault(PLUGIN, "");
        PluginConfig current = Optional.ofNullable(ViewEditing.plugin(view.options(display), kind))
                .orElse(PluginConfig.of(""));
        boolean same = chosen.equals(current.plugin());
        Map<String, Object> settings = !plugins.has(chosen) ? Map.of()
                : same ? plugins.get(chosen).settingsValues(SETTINGS_PREFIX, submitted) : Map.of();
        PluginConfig candidate = new PluginConfig(chosen, settings);
        FormElement tree = pluginForm(view, display, kind, same ? candidate : current);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        if (!state.hasErrors() && !plugins.has(chosen)) {
            state.error(PLUGIN, "Choose a plugin.");
        }
        if (state.hasErrors()) {
            return formPage("Configure " + SECTION_LABELS.get(kind), displayPath(id, display) + "/plugin/" + kind,
                    tree, state.errors(), model);
        }
        views.save(ViewEditing.editPlugin(view, display, kind, candidate));
        return same ? "redirect:" + editPath(id) + "?display=" + display
                : "redirect:" + displayPath(id, display) + "/plugin/" + kind;
    }

    private FormElement pluginForm(ViewConfig view, String display, String kind, PluginConfig current) {
        PluginManager<? extends ViewPlugin> plugins = registry.managerFor(ViewEditing.PLUGIN_TYPES.get(kind));
        FormElement form = FormElement.of(ElementType.CONTAINER, "plugin-settings")
                .child(FormElement.of(ElementType.SELECT, PLUGIN).label(SECTION_LABELS.get(kind)).markRequired()
                        .value(current.plugin())
                        .description("Choosing another plugin saves it and opens its settings.")
                        .options(plugins.ids().stream().map(each -> new SelectOption(each, plugins.get(each).label()))
                                .sorted(Comparator.comparing(SelectOption::label)).toList()));
        if (plugins.has(current.plugin())) {
            plugins.get(current.plugin()).settingsForm(SETTINGS_PREFIX, current.settings()).forEach(form::child);
        }
        return form.child(actions("Apply", editPath(view.id()) + "?display=" + display));
    }

    @PostMapping(PATH + "/view/{id}/displays/add")
    public String addDisplay(@PathVariable String id, @RequestParam(name = PLUGIN, defaultValue = "") String plugin) {
        ViewConfig view = viewOf(id);
        if (!List.of(ViewDisplay.PAGE, ViewDisplay.BLOCK, ViewDisplay.FEED).contains(plugin)) {
            return "redirect:" + editPath(id);
        }
        int number = 1;
        while (view.display(plugin + "_" + number).isPresent()) {
            number++;
        }
        String displayId = plugin + "_" + number;
        Map<String, Object> settings = switch (plugin) {
            case ViewDisplay.PAGE -> Map.of(ViewDisplay.PATH, "/" + view.id().replace('_', '-')
                    + (number > 1 ? "-" + number : ""));
            case ViewDisplay.FEED -> Map.of(ViewDisplay.PATH, "/" + view.id().replace('_', '-') + "/feed"
                    + (number > 1 ? "-" + number : ""));
            default -> Map.of();
        };
        views.save(view.withDisplay(new ViewDisplay(displayId, plugin, view.label(), ViewOptions.INHERIT,
                settings)));
        return "redirect:" + editPath(id) + "?display=" + displayId;
    }

    @PostMapping(PATH + "/view/{id}/display/{display}/delete")
    public String deleteDisplay(@PathVariable String id, @PathVariable String display) {
        ViewConfig view = viewOf(id);
        displayOf(view, display);
        if (!display.equals(ViewDisplay.DEFAULT)) {
            views.save(view.withoutDisplay(display));
        }
        return "redirect:" + editPath(id);
    }

    @GetMapping(PATH + "/view/{id}/delete")
    public String confirmDelete(@PathVariable String id, Model model) {
        ViewConfig view = viewOf(id);
        model.addAttribute("title", "Delete the view " + view.label() + "?");
        model.addAttribute("description", "Its pages, blocks, and feeds go with it. This action cannot be undone.");
        model.addAttribute("action", editPath(id) + "/delete");
        model.addAttribute("formMarkup", forms.render(FormElement.of(ElementType.CONTAINER, "confirm")
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "confirm").label("Delete view")
                                .attribute("class", "btn btn-danger"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(editPath(id))))));
        return "admin/block-form";
    }

    @PostMapping(PATH + "/view/{id}/delete")
    public String delete(@PathVariable String id) {
        views.delete(viewOf(id).id());
        return "redirect:" + PATH;
    }

    private static FormElement actions(String submitLabel, String cancelPath) {
        return FormElement.of(ElementType.ACTIONS, "actions")
                .child(FormElement.of(ElementType.SUBMIT, "save").label(submitLabel))
                .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(cancelPath));
    }

    private String formPage(String title, String action, FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", title);
        model.addAttribute("description", "");
        model.addAttribute("action", action);
        model.addAttribute("formMarkup", forms.render(tree, errors));
        return "admin/block-form";
    }

    private ViewConfig viewOf(String id) {
        return views.find(id).orElseThrow(() -> new EntityNotFoundException("view", id));
    }

    private static ViewDisplay displayOf(ViewConfig view, String display) {
        return view.display(display).orElseThrow(() -> new EntityNotFoundException("view display", display));
    }

    private static String sectionOf(String section) {
        if (!ViewEditing.SECTIONS.contains(section)) {
            throw new EntityNotFoundException("view section", section);
        }
        return section;
    }

    private static String kindOf(String kind) {
        if (!ViewEditing.KINDS.contains(kind)) {
            throw new EntityNotFoundException("view plugin", kind);
        }
        return kind;
    }

    private static HandlerConfig handlerOf(ViewConfig view, String display, String section, String handler) {
        List<HandlerConfig> handlers = ViewEditing.section(view.options(display), section);
        return (handlers == null ? List.<HandlerConfig>of() : handlers).stream()
                .filter(each -> each.id().equals(handler)).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("view handler", handler));
    }
}
