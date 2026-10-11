package dev.springdrop.web;

import dev.springdrop.kernel.action.Action;
import dev.springdrop.kernel.action.ActionConfig;
import dev.springdrop.kernel.action.ActionManager;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.plugin.PluginManager;
import dev.springdrop.kernel.plugin.PluginRegistry;
import dev.springdrop.kernel.text.MachineNameGenerator;
import dev.springdrop.kernel.web.EntityNotFoundException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The actions the site offers. Simple actions are listed as they are. A
 * configurable action is offered once an administrator creates it here with
 * its settings, and can then be edited or deleted.
 */
@Controller
public class ActionAdminController {

    public static final String PATH = "/admin/config/system/actions";

    public static final String ADD_PATH = PATH + "/add";

    public static final String PLUGIN = "plugin";

    public static final String LABEL = "label";

    private final ActionManager actions;
    private final PluginRegistry plugins;
    private final ConfigStore config;
    private final MachineNameGenerator machineNames;
    private final FormBuilder formBuilder;
    private final FormRenderer renderer;

    public ActionAdminController(ActionManager actions, PluginRegistry plugins, ConfigStore config,
            MachineNameGenerator machineNames, FormBuilder formBuilder, FormRenderer renderer) {
        this.actions = actions;
        this.plugins = plugins;
        this.config = config;
        this.machineNames = machineNames;
        this.formBuilder = formBuilder;
        this.renderer = renderer;
    }

    public static String managePath(String id) {
        return PATH + "/manage/" + id;
    }

    /** A row of the listing: the action, the type it works on, and whether it is stored and so editable. */
    public record ActionRow(String id, String label, String entityType, boolean editable) {
    }

    @GetMapping(PATH)
    public String list(Model model) {
        model.addAttribute("title", "Actions");
        model.addAttribute("description", "What can be done to content and accounts, one at a time or to a "
                + "selection in a listing.");
        model.addAttribute("rows", actions.actions().stream().map(action -> new ActionRow(action.id(),
                action.label(), actions.plugin(action).entityType(), actions.plugin(action).configurable())).toList());
        model.addAttribute("configurable", configurablePlugins().stream()
                .map(id -> new SelectOption(id, manager().get(id).label())).toList());
        model.addAttribute("addPath", ADD_PATH);
        model.addAttribute("managePath", PATH + "/manage/");
        return "admin/actions";
    }

    @GetMapping(ADD_PATH)
    public String addForm(@RequestParam(name = PLUGIN, defaultValue = "") String plugin, Model model) {
        Action action = configurablePlugin(plugin);
        return formPage("Add " + action.label(), ADD_PATH, settingsForm(plugin, action, Map.of()), Map.of(), model);
    }

    @PostMapping(ADD_PATH)
    public String add(@RequestParam Map<String, String> submitted, Model model) {
        String plugin = submitted.getOrDefault(PLUGIN, "");
        Action action = configurablePlugin(plugin);
        String label = submitted.getOrDefault(LABEL, "");
        String id = label.isBlank() ? "" : machineNames.generateUnique(label,
                taken -> config.listNames(ActionConfig.CONFIG_PREFIX).contains(ActionConfig.configName(taken))
                        || manager().has(taken));
        return save(id, plugin, action, submitted, "Add " + action.label(), ADD_PATH, model);
    }

    @GetMapping(PATH + "/manage/{id}")
    public String editForm(@PathVariable String id, Model model) {
        ActionConfig stored = storedAction(id);
        Map<String, Object> values = new LinkedHashMap<>(stored.configuration());
        values.put(LABEL, stored.label());
        return formPage("Edit " + stored.label(), managePath(id),
                settingsForm(stored.plugin(), actions.plugin(stored), values), Map.of(), model);
    }

    @PostMapping(PATH + "/manage/{id}")
    public String edit(@PathVariable String id, @RequestParam Map<String, String> submitted, Model model) {
        ActionConfig stored = storedAction(id);
        return save(id, stored.plugin(), actions.plugin(stored), submitted, "Edit " + stored.label(),
                managePath(id), model);
    }

    @GetMapping(PATH + "/manage/{id}/delete")
    public String confirmDelete(@PathVariable String id, Model model) {
        ActionConfig stored = storedAction(id);
        FormElement confirm = FormElement.of(ElementType.CONTAINER, "confirm")
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "confirm").label("Delete action"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH)));
        model.addAttribute("title", "Delete the action " + stored.label() + "?");
        model.addAttribute("description", "This action cannot be undone.");
        model.addAttribute("action", managePath(id) + "/delete");
        model.addAttribute("formMarkup", renderer.render(confirm));
        return "admin/block-form";
    }

    @PostMapping(PATH + "/manage/{id}/delete")
    public String delete(@PathVariable String id) {
        storedAction(id);
        actions.delete(id);
        return "redirect:" + PATH;
    }

    private String save(String id, String plugin, Action action, Map<String, String> submitted, String title,
            String path, Model model) {
        FormElement tree = settingsForm(plugin, action, new LinkedHashMap<>(submitted));
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        Map<String, Object> configuration = new LinkedHashMap<>();
        action.configurationForm(Map.of()).forEach(element ->
                configuration.put(element.name(), submitted.getOrDefault(element.name(), "")));
        if (!state.hasErrors()) {
            actions.save(new ActionConfig(id, submitted.get(LABEL), plugin, configuration)).forEach(state::error);
        }
        if (state.hasErrors()) {
            return formPage(title, path, tree, state.errors(), model);
        }
        return "redirect:" + PATH;
    }

    private static FormElement settingsForm(String plugin, Action action, Map<String, Object> values) {
        FormElement form = FormElement.of(ElementType.CONTAINER, "action-form")
                .child(FormElement.of(ElementType.HIDDEN, PLUGIN).value(plugin))
                .child(FormElement.of(ElementType.TEXTFIELD, LABEL).label("Label").markRequired()
                        .description("The name the action is offered under.")
                        .value(Objects.toString(values.get(LABEL), ""))
                        .rule(ValidationRule.maxLength(255)));
        action.configurationForm(values).forEach(form::child);
        return form.child(FormElement.of(ElementType.ACTIONS, "actions")
                .child(FormElement.of(ElementType.SUBMIT, "save").label("Save action"))
                .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH)));
    }

    private String formPage(String title, String path, FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", title);
        model.addAttribute("description", "An action with settings of its own, offered under its label.");
        model.addAttribute("action", path);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/block-form";
    }

    private PluginManager<Action> manager() {
        return plugins.managerFor(Action.class);
    }

    private List<String> configurablePlugins() {
        return manager().ids().stream().filter(id -> manager().get(id).configurable()).sorted().toList();
    }

    private Action configurablePlugin(String plugin) {
        if (!configurablePlugins().contains(plugin)) {
            throw new EntityNotFoundException("configurable action", plugin);
        }
        return manager().get(plugin);
    }

    private ActionConfig storedAction(String id) {
        return actions.find(id).filter(action -> actions.plugin(action).configurable())
                .orElseThrow(() -> new EntityNotFoundException("action", id));
    }
}
