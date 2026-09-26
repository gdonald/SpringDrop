package dev.springdrop.web;

import dev.springdrop.kernel.block.BlockManager;
import dev.springdrop.kernel.block.BlockPlacement;
import dev.springdrop.kernel.block.BlockPlacementManager;
import dev.springdrop.kernel.block.BlockPlugin;
import dev.springdrop.kernel.block.ConditionConfig;
import dev.springdrop.kernel.block.ConditionDefinition;
import dev.springdrop.kernel.block.VisibilityCondition;
import dev.springdrop.kernel.block.VisibilityConditionManager;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.text.MachineNameGenerator;
import dev.springdrop.kernel.theme.Region;
import dev.springdrop.kernel.theme.Tab;
import dev.springdrop.kernel.theme.Theme;
import dev.springdrop.kernel.theme.ThemeRegistry;
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
 * The block layout: which blocks each theme shows, in which region, in what
 * order, and under what conditions. Blocks are placed from the library of block
 * plugins, and each placement is configured with its plugin's own settings and
 * the visibility conditions it shows under.
 */
@Controller
public class BlockLayoutController {

    public static final String PATH = "/admin/structure/block";

    public static final String LABEL = "label";

    public static final String LABEL_DISPLAY = "label_display";

    public static final String REGION = "region";

    public static final String WEIGHT = "weight";

    /** The prefix of each region choice on the layout, one per placed block. */
    public static final String REGION_PREFIX = "region_";

    /** The prefix of each weight input on the layout, one per placed block. */
    public static final String WEIGHT_PREFIX = "weight_";

    /** The prefix every visibility element is named under, followed by its condition. */
    public static final String VISIBILITY_PREFIX = "visibility_";

    public static final String NEGATE = "negate";

    public static final String WHOLE_NUMBER = "-?\\d{1,9}";

    public static final String UNKNOWN_REGION_MESSAGE = "Choose one of this theme's regions.";

    private final BlockPlacementManager placements;
    private final BlockManager blocks;
    private final VisibilityConditionManager conditions;
    private final ThemeRegistry themes;
    private final MachineNameGenerator machineNames;
    private final FormBuilder forms;
    private final FormRenderer renderer;

    public BlockLayoutController(
            BlockPlacementManager placements,
            BlockManager blocks,
            VisibilityConditionManager conditions,
            ThemeRegistry themes,
            MachineNameGenerator machineNames,
            FormBuilder forms,
            FormRenderer renderer) {
        this.placements = placements;
        this.blocks = blocks;
        this.conditions = conditions;
        this.themes = themes;
        this.machineNames = machineNames;
        this.forms = forms;
        this.renderer = renderer;
    }

    public static String layoutPath(String theme) {
        return PATH + "/list/" + theme;
    }

    /** The block layout of the front-end theme, which is the one most sites edit most. */
    @GetMapping(PATH)
    public String defaultLayout(Model model) {
        return layout(Theme.FRONT_END, model);
    }

    @GetMapping(PATH + "/list/{theme}")
    public String layout(@PathVariable String theme, Model model) {
        List<Region> regions = regionsOf(theme);

        model.addAttribute("title", "Block layout");
        model.addAttribute("description", "The blocks each region of the theme shows, in the order they show.");
        model.addAttribute("tabs", themes.names().stream()
                .sorted()
                .map(name -> new Tab(name, layoutPath(name), name.equals(theme)))
                .toList());
        model.addAttribute("action", layoutPath(theme));
        model.addAttribute("regions", layoutRegions(theme, regions));
        model.addAttribute("regionOptions", regions);
        model.addAttribute("libraryPath", PATH + "/library/" + theme + "?region=");
        model.addAttribute("managePath", PATH + "/manage/");
        model.addAttribute("regionPrefix", REGION_PREFIX);
        model.addAttribute("weightPrefix", WEIGHT_PREFIX);
        return "admin/block-layout";
    }

    /**
     * Saves the region and weight of every block in the theme in one go, which
     * is what the layout table submits. A region the theme does not have or a
     * weight that is not a whole number leaves that block where it was.
     */
    @PostMapping(PATH + "/list/{theme}")
    public String saveLayout(@PathVariable String theme, @RequestParam Map<String, String> submitted) {
        List<String> regionIds = regionsOf(theme).stream().map(Region::id).toList();
        for (BlockPlacement placement : placements.inTheme(theme)) {
            BlockPlacement moved = placement;
            String region = submitted.get(REGION_PREFIX + placement.id());
            if (region != null && regionIds.contains(region)) {
                moved = moved.inRegion(region);
            }
            String weight = submitted.get(WEIGHT_PREFIX + placement.id());
            if (weight != null && weight.matches(WHOLE_NUMBER)) {
                moved = moved.withWeight(Integer.parseInt(weight));
            }
            if (!moved.equals(placement)) {
                placements.save(moved);
            }
        }
        return "redirect:" + layoutPath(theme);
    }

    @GetMapping(PATH + "/library/{theme}")
    public String library(
            @PathVariable String theme,
            @RequestParam(name = REGION, defaultValue = Region.CONTENT) String region,
            Model model) {

        regionsOf(theme);
        model.addAttribute("title", "Place a block");
        model.addAttribute("description", "Choose the block to place in the " + region + " region.");
        model.addAttribute("definitions", blocks.definitions());
        model.addAttribute("addPath", PATH + "/add/");
        model.addAttribute("theme", theme);
        model.addAttribute("region", region);
        model.addAttribute("cancelPath", layoutPath(theme));
        return "admin/block-library";
    }

    @GetMapping(PATH + "/add/{plugin}/{theme}")
    public String addForm(
            @PathVariable String plugin,
            @PathVariable String theme,
            @RequestParam(name = REGION, defaultValue = Region.CONTENT) String region,
            Model model) {

        BlockPlacement blank = BlockPlacement.of(null, theme, region, plugin, pluginOf(plugin).label());
        regionsOf(theme);
        return formPage("Place " + blank.label(), PATH + "/add/" + plugin + "/" + theme,
                placementForm(blank), Map.of(), model);
    }

    @PostMapping(PATH + "/add/{plugin}/{theme}")
    public String add(
            @PathVariable String plugin,
            @PathVariable String theme,
            @RequestParam Map<String, String> submitted,
            Model model) {

        BlockPlacement blank = BlockPlacement.of(null, theme, Region.CONTENT, plugin, pluginOf(plugin).label());
        regionsOf(theme);
        return save(blank, submitted, "Place " + blank.label(), PATH + "/add/" + plugin + "/" + theme, model);
    }

    @GetMapping(PATH + "/manage/{id}")
    public String editForm(@PathVariable String id, Model model) {
        BlockPlacement placement = placementOf(id);
        return formPage("Configure " + placement.label(), PATH + "/manage/" + id,
                placementForm(placement), Map.of(), model);
    }

    @PostMapping(PATH + "/manage/{id}")
    public String edit(@PathVariable String id, @RequestParam Map<String, String> submitted, Model model) {
        BlockPlacement placement = placementOf(id);
        return save(placement, submitted, "Configure " + placement.label(), PATH + "/manage/" + id, model);
    }

    @GetMapping(PATH + "/manage/{id}/delete")
    public String confirmDelete(@PathVariable String id, Model model) {
        BlockPlacement placement = placementOf(id);
        FormElement confirm = FormElement.of(ElementType.CONTAINER, "confirm")
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "confirm").label("Remove block"))
                        .child(FormElement.of(ElementType.LINK, "cancel")
                                .label("Cancel")
                                .value(layoutPath(placement.theme()))));
        model.addAttribute("title", "Remove the block " + placement.label() + "?");
        model.addAttribute("description", "The block leaves the " + placement.region()
                + " region. What it showed is not deleted.");
        model.addAttribute("action", PATH + "/manage/" + id + "/delete");
        model.addAttribute("formMarkup", renderer.render(confirm));
        return "admin/block-form";
    }

    @PostMapping(PATH + "/manage/{id}/delete")
    public String delete(@PathVariable String id) {
        BlockPlacement placement = placementOf(id);
        placements.delete(id);
        return "redirect:" + layoutPath(placement.theme());
    }

    /**
     * Reads the submission into a placement and saves it once every element's
     * rules, the region, and the plugin's own checks pass. Otherwise the form
     * comes back holding what was submitted, with each error on its field.
     */
    private String save(
            BlockPlacement base, Map<String, String> submitted, String title, String action, Model model) {

        BlockPlugin plugin = pluginOf(base.plugin());
        BlockPlacement candidate = submittedPlacement(base, plugin, submitted);
        FormElement tree = placementForm(candidate);

        FormState state = forms.validate(tree, new LinkedHashMap<>(submitted));
        if (regionsOf(base.theme()).stream().noneMatch(region -> region.id().equals(candidate.region()))) {
            state.error(REGION, UNKNOWN_REGION_MESSAGE);
        }
        plugin.validateSettings(submitted).forEach(state::error);
        if (state.hasErrors()) {
            return formPage(title, action, tree, state.errors(), model);
        }

        String id = (base.id() != null) ? base.id() : machineNames.generateUnique(
                base.theme() + " " + candidate.label(), taken -> placements.find(taken).isPresent());
        placements.save(new BlockPlacement(id, candidate.theme(), candidate.region(), candidate.plugin(),
                candidate.label(), candidate.labelDisplay(), candidate.weight(), candidate.settings(),
                candidate.visibility()));
        return "redirect:" + layoutPath(base.theme());
    }

    private BlockPlacement submittedPlacement(
            BlockPlacement base, BlockPlugin plugin, Map<String, String> submitted) {

        String weight = submitted.getOrDefault(WEIGHT, "");
        List<ConditionConfig> visibility = new ArrayList<>();
        for (ConditionDefinition definition : conditions.definitions()) {
            String prefix = conditionPrefix(definition.id());
            conditions.condition(definition.id()).settingsValues(prefix, submitted).ifPresent(settings -> {
                ConditionConfig condition = ConditionConfig.of(definition.id(), settings);
                visibility.add(submitted.containsKey(prefix + NEGATE) ? condition.negated() : condition);
            });
        }
        return new BlockPlacement(
                base.id(),
                base.theme(),
                submitted.getOrDefault(REGION, ""),
                base.plugin(),
                submitted.getOrDefault(LABEL, ""),
                submitted.containsKey(LABEL_DISPLAY),
                weight.matches(WHOLE_NUMBER) ? Integer.parseInt(weight) : base.weight(),
                plugin.settingsValues(submitted),
                visibility);
    }

    private FormElement placementForm(BlockPlacement placement) {
        FormElement form = FormElement.of(ElementType.CONTAINER, "block")
                .child(FormElement.of(ElementType.TEXTFIELD, LABEL)
                        .label("Title")
                        .markRequired()
                        .value(placement.label())
                        .rule(ValidationRule.maxLength(255)))
                .child(FormElement.of(ElementType.CHECKBOX, LABEL_DISPLAY)
                        .label("Display title")
                        .value(placement.labelDisplay()))
                .child(FormElement.of(ElementType.SELECT, REGION)
                        .label("Region")
                        .markRequired()
                        .value(placement.region())
                        .options(regionsOf(placement.theme()).stream()
                                .map(region -> new SelectOption(region.id(), region.label()))
                                .toList()))
                .child(FormElement.of(ElementType.NUMBER, WEIGHT)
                        .label("Weight")
                        .description("Lighter blocks come first in their region.")
                        .value(placement.weight())
                        .rule(ValidationRule.pattern(WHOLE_NUMBER).withMessage("The weight is a whole number.")));

        List<FormElement> settings = pluginOf(placement.plugin()).settingsForm(placement.settings());
        if (!settings.isEmpty()) {
            FormElement fieldset = FormElement.of(ElementType.FIELDSET, "settings").label("Block settings");
            settings.forEach(fieldset::child);
            form.child(fieldset);
        }

        FormElement visibility = FormElement.of(ElementType.FIELDSET, "visibility").label("Visibility");
        for (ConditionDefinition definition : conditions.definitions()) {
            visibility.child(conditionForm(definition, placement));
        }
        return form.child(visibility)
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save block"))
                        .child(FormElement.of(ElementType.LINK, "cancel")
                                .label("Cancel")
                                .value(layoutPath(placement.theme()))));
    }

    /** One condition's settings, filled from what the placement already carries for it. */
    private FormElement conditionForm(ConditionDefinition definition, BlockPlacement placement) {
        String prefix = conditionPrefix(definition.id());
        Optional<ConditionConfig> stored = placement.visibility().stream()
                .filter(condition -> condition.plugin().equals(definition.id()))
                .findFirst();
        VisibilityCondition condition = conditions.condition(definition.id());

        FormElement details = FormElement.of(ElementType.DETAILS, prefix + "details").label(definition.label());
        condition.settingsForm(prefix, stored.map(ConditionConfig::settings).orElse(Map.of()))
                .forEach(details::child);
        return details.child(FormElement.of(ElementType.CHECKBOX, prefix + NEGATE)
                .label("Show everywhere except where this matches")
                .value(stored.map(ConditionConfig::negate).orElse(false)));
    }

    /** The element names of a condition start with its id, which a derivative joins with a colon. */
    public static String conditionPrefix(String conditionId) {
        return VISIBILITY_PREFIX + conditionId.replace(':', '_') + "_";
    }

    private String formPage(
            String title, String action, FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", title);
        model.addAttribute("description", "Where the block shows, what it shows, and when.");
        model.addAttribute("action", action);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/block-form";
    }

    private List<BlockLayoutRegion> layoutRegions(String theme, List<Region> regions) {
        List<BlockPlacement> placed = placements.inTheme(theme);
        return regions.stream()
                .map(region -> new BlockLayoutRegion(region.id(), region.label(), placed.stream()
                        .filter(placement -> placement.region().equals(region.id()))
                        .map(placement -> new BlockLayoutRow(placement.id(), placement.label(),
                                blockLabel(placement.plugin()), placement.region(), placement.weight()))
                        .toList()))
                .toList();
    }

    private String blockLabel(String pluginId) {
        return blocks.has(pluginId) ? blocks.plugin(pluginId).label() : pluginId + " (missing)";
    }

    private List<Region> regionsOf(String theme) {
        if (themes.find(theme).isEmpty()) {
            throw new EntityNotFoundException("theme", theme);
        }
        return themes.regions(theme);
    }

    private BlockPlugin pluginOf(String pluginId) {
        if (!blocks.has(pluginId)) {
            throw new EntityNotFoundException("block plugin", pluginId);
        }
        return blocks.plugin(pluginId);
    }

    private BlockPlacement placementOf(String id) {
        return placements.find(id).orElseThrow(() -> new EntityNotFoundException("block", id));
    }
}
