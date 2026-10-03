package dev.springdrop.web;

import dev.springdrop.kernel.filter.FilterConfig;
import dev.springdrop.kernel.filter.TextFilter;
import dev.springdrop.kernel.filter.TextFormat;
import dev.springdrop.kernel.filter.TextFormatManager;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.plugin.PluginManager;
import dev.springdrop.kernel.plugin.PluginRegistry;
import dev.springdrop.kernel.role.RoleConfig;
import dev.springdrop.kernel.role.RoleManager;
import dev.springdrop.kernel.text.MachineNameGenerator;
import dev.springdrop.kernel.web.EntityNotFoundException;
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
 * The site's text formats: which roles may write in each, and which filters it
 * runs, in what order, with what settings. The fallback format is open to
 * everyone, so it has no roles to choose and cannot be deleted.
 */
@Controller
public class TextFormatController {

    public static final String PATH = "/admin/config/content/formats";

    public static final String LABEL = "label";

    public static final String WEIGHT = "weight";

    /** The prefix of each role's box, followed by the role's id. */
    public static final String ROLE_PREFIX = "role_";

    /** The prefix of every element of one filter, followed by the filter's id and an underscore. */
    public static final String FILTER_PREFIX = "filter_";

    public static final String ENABLED = "enabled";

    private final TextFormatManager formats;
    private final RoleManager roles;
    private final PluginRegistry registry;
    private final MachineNameGenerator machineNames;
    private final FormBuilder formBuilder;
    private final FormRenderer renderer;

    public TextFormatController(TextFormatManager formats, RoleManager roles, PluginRegistry registry,
            MachineNameGenerator machineNames, FormBuilder formBuilder, FormRenderer renderer) {
        this.formats = formats;
        this.roles = roles;
        this.registry = registry;
        this.machineNames = machineNames;
        this.formBuilder = formBuilder;
        this.renderer = renderer;
    }

    public static String managePath(String id) {
        return PATH + "/manage/" + id;
    }

    /** The element names of one filter start with its id. */
    public static String filterPrefix(String filterId) {
        return FILTER_PREFIX + filterId + "_";
    }

    @GetMapping(PATH)
    public String list(Model model) {
        List<TextFormatRow> rows = formats.all().stream()
                .map(format -> new TextFormatRow(format.id(), format.label(), format.fallback() ? "All roles"
                        : String.join(", ", roles.all().stream()
                                .filter(role -> role.permissions().contains(TextFormatManager.permission(format.id())))
                                .map(RoleConfig::label)
                                .toList()),
                        format.fallback()))
                .toList();
        model.addAttribute("title", "Text formats");
        model.addAttribute("description", "What text written in each format becomes, and who may write in it.");
        model.addAttribute("rows", rows);
        model.addAttribute("addPath", PATH + "/add");
        model.addAttribute("managePath", PATH + "/manage/");
        return "admin/text-formats";
    }

    @GetMapping(PATH + "/add")
    public String addForm(Model model) {
        return formPage("Add text format", PATH + "/add",
                formatForm(new TextFormat("", "", 0, false, List.of())), Map.of(), model);
    }

    @PostMapping(PATH + "/add")
    public String add(@RequestParam Map<String, String> submitted, Model model) {
        String label = submitted.getOrDefault(LABEL, "");
        String id = label.isBlank() ? "" : machineNames.generateUnique(label, taken -> formats.find(taken).isPresent());
        return save(new TextFormat(id, label, 0, false, List.of()), submitted, "Add text format", PATH + "/add", model);
    }

    @GetMapping(PATH + "/manage/{id}")
    public String editForm(@PathVariable String id, Model model) {
        TextFormat format = formatOf(id);
        return formPage("Configure " + format.label(), managePath(id), formatForm(format), Map.of(), model);
    }

    @PostMapping(PATH + "/manage/{id}")
    public String edit(@PathVariable String id, @RequestParam Map<String, String> submitted, Model model) {
        TextFormat format = formatOf(id);
        return save(format, submitted, "Configure " + format.label(), managePath(id), model);
    }

    @GetMapping(PATH + "/manage/{id}/delete")
    public String confirmDelete(@PathVariable String id, Model model) {
        TextFormat format = deletableFormatOf(id);
        FormElement confirm = FormElement.of(ElementType.CONTAINER, "confirm")
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "confirm").label("Delete text format"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH)));
        model.addAttribute("title", "Delete the text format " + format.label() + "?");
        model.addAttribute("description", "Text written in it is shown the way " + formats.fallback().label()
                + " shows text. This action cannot be undone.");
        model.addAttribute("action", managePath(id) + "/delete");
        model.addAttribute("formMarkup", renderer.render(confirm));
        return "admin/block-form";
    }

    @PostMapping(PATH + "/manage/{id}/delete")
    public String delete(@PathVariable String id) {
        deletableFormatOf(id);
        roles.all().forEach(role -> roles.revoke(role.id(), TextFormatManager.permission(id)));
        formats.delete(id);
        return "redirect:" + PATH;
    }

    /**
     * Saves the format once the form's rules pass: its label and weight, the
     * filters ticked with their weights and settings, and, for any format but
     * the fallback, which roles may write in it.
     */
    private String save(TextFormat base, Map<String, String> submitted, String title, String action, Model model) {
        TextFormat candidate = new TextFormat(base.id(), submitted.getOrDefault(LABEL, ""),
                weightOf(submitted.getOrDefault(WEIGHT, "")), base.fallback(), chosenFilters(submitted));
        FormElement tree = formatForm(candidate);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        if (state.hasErrors()) {
            return formPage(title, action, tree, state.errors(), model);
        }
        formats.save(candidate);
        if (!candidate.fallback()) {
            for (RoleConfig role : roles.all()) {
                if (submitted.containsKey(ROLE_PREFIX + role.id())) {
                    roles.grant(role.id(), TextFormatManager.permission(candidate.id()));
                } else {
                    roles.revoke(role.id(), TextFormatManager.permission(candidate.id()));
                }
            }
        }
        return "redirect:" + PATH;
    }

    private List<FilterConfig> chosenFilters(Map<String, String> submitted) {
        PluginManager<TextFilter> filters = registry.managerFor(TextFilter.class);
        List<FilterConfig> chosen = new ArrayList<>();
        for (String filterId : filters.ids().stream().sorted().toList()) {
            String prefix = filterPrefix(filterId);
            if (submitted.containsKey(prefix + ENABLED)) {
                chosen.add(new FilterConfig(filterId, weightOf(submitted.getOrDefault(prefix + WEIGHT, "")),
                        filters.get(filterId).settingsValues(prefix, submitted)));
            }
        }
        return chosen;
    }

    private static int weightOf(String weight) {
        return weight.matches(BlockLayoutController.WHOLE_NUMBER) ? Integer.parseInt(weight) : 0;
    }

    /**
     * The format's label, weight, the roles that may use it, and for each
     * filter the site has whether the format runs it, where, and its settings.
     */
    private FormElement formatForm(TextFormat format) {
        FormElement form = FormElement.of(ElementType.CONTAINER, "text-format")
                .child(FormElement.of(ElementType.TEXTFIELD, LABEL).label("Name").markRequired()
                        .value(format.label())
                        .rule(ValidationRule.maxLength(255)))
                .child(weightElement(WEIGHT, format.weight()));
        if (!format.fallback()) {
            FormElement roleSet = FormElement.of(ElementType.FIELDSET, "roles").label("Roles");
            for (RoleConfig role : roles.all()) {
                roleSet.child(FormElement.of(ElementType.CHECKBOX, ROLE_PREFIX + role.id()).label(role.label())
                        .value(role.permissions().contains(TextFormatManager.permission(format.id()))));
            }
            form.child(roleSet);
        }
        PluginManager<TextFilter> filters = registry.managerFor(TextFilter.class);
        FormElement filterSet = FormElement.of(ElementType.FIELDSET, "filters").label("Filters");
        for (String filterId : filters.ids().stream().sorted().toList()) {
            TextFilter filter = filters.get(filterId);
            String prefix = filterPrefix(filterId);
            Optional<FilterConfig> enabled = format.filter(filterId);
            FormElement details = FormElement.of(ElementType.DETAILS, prefix + "details").label(filter.label())
                    .child(FormElement.of(ElementType.CHECKBOX, prefix + ENABLED).label("Enabled")
                            .value(enabled.isPresent()))
                    .child(weightElement(prefix + WEIGHT, enabled.map(FilterConfig::weight).orElse(0)));
            filter.settingsForm(prefix, enabled.map(FilterConfig::settings).orElse(filter.defaultSettings()))
                    .forEach(details::child);
            filterSet.child(details);
        }
        return form.child(filterSet)
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save configuration"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH)));
    }

    private static FormElement weightElement(String name, int weight) {
        return FormElement.of(ElementType.NUMBER, name).label("Weight").value(weight)
                .rule(ValidationRule.pattern(BlockLayoutController.WHOLE_NUMBER)
                        .withMessage("The weight is a whole number."));
    }

    private String formPage(String title, String action, FormElement tree, Map<String, String> errors,
            Model model) {
        model.addAttribute("title", title);
        model.addAttribute("description", "What text written in this format becomes, and who may write in it.");
        model.addAttribute("action", action);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/block-form";
    }

    private TextFormat formatOf(String id) {
        return formats.find(id).orElseThrow(() -> new EntityNotFoundException("text format", id));
    }

    private TextFormat deletableFormatOf(String id) {
        return formats.find(id).filter(format -> !format.fallback())
                .orElseThrow(() -> new EntityNotFoundException("deletable text format", id));
    }
}
