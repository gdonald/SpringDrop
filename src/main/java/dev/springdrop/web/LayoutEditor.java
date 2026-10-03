package dev.springdrop.web;

import dev.springdrop.kernel.block.BlockInstance;
import dev.springdrop.kernel.block.BlockManager;
import dev.springdrop.kernel.block.BlockPlugin;
import dev.springdrop.kernel.block.plugins.FieldBlockDeriver;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.layout.LayoutManager;
import dev.springdrop.kernel.layout.LayoutRegion;
import dev.springdrop.kernel.layout.Section;
import dev.springdrop.kernel.layout.SectionComponent;
import dev.springdrop.kernel.web.EntityNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/**
 * The editing a layout takes, whichever layout it is: adding and removing
 * sections, choosing each section's column widths, and placing, configuring,
 * moving, and removing the blocks in its regions. The blocks on offer are the
 * theme's blocks and a field block for each of the bundle's fields. Each
 * handler answers with the view to show or the redirect to follow.
 */
@Component
public class LayoutEditor {

    public static final String LAYOUT = "layout";

    public static final String LABEL = "label";

    public static final String LABEL_DISPLAY = "label_display";

    /** The prefix of each section's width choice, followed by the section's index. */
    public static final String WIDTHS_PREFIX = "widths_";

    /** The prefix of each block's region choice, followed by the block's id. */
    public static final String REGION_PREFIX = "region_";

    /** The prefix of each block's weight input, followed by the block's id. */
    public static final String WEIGHT_PREFIX = "weight_";

    private final LayoutManager layouts;
    private final BlockManager blocks;
    private final FormBuilder forms;
    private final FormRenderer renderer;

    public LayoutEditor(LayoutManager layouts, BlockManager blocks, FormBuilder forms, FormRenderer renderer) {
        this.layouts = layouts;
        this.blocks = blocks;
        this.forms = forms;
        this.renderer = renderer;
    }

    /** Puts the sections, the layouts a new section may use, and the input names on the editor page. */
    void describe(LayoutTarget target, Model model) {
        model.addAttribute("action", target.path());
        model.addAttribute("sections", editorSections(target.sections()));
        model.addAttribute("layoutOptions", layouts.definitions());
        model.addAttribute("widthsPrefix", WIDTHS_PREFIX);
        model.addAttribute("regionPrefix", REGION_PREFIX);
        model.addAttribute("weightPrefix", WEIGHT_PREFIX);
    }

    /**
     * Every section with the widths, regions, and weights the editor submitted.
     * Widths the layout does not offer, a region the section does not have, or a
     * weight that is not a whole number leaves that part as it was.
     */
    List<Section> arranged(List<Section> sections, Map<String, String> submitted) {
        List<Section> arranged = new ArrayList<>();
        for (int index = 0; index < sections.size(); index++) {
            arranged.add(arranged(sections.get(index), index, submitted));
        }
        return arranged;
    }

    String addSection(LayoutTarget target, String layout) {
        if (!layouts.has(layout)) {
            throw new EntityNotFoundException("layout", layout);
        }
        List<Section> changed = new ArrayList<>(target.sections());
        changed.add(Section.of(layout));
        target.save(changed);
        return redirect(target);
    }

    String removeSection(LayoutTarget target, int index) {
        sectionOf(target, index);
        List<Section> changed = new ArrayList<>(target.sections());
        changed.remove(index);
        target.save(changed);
        return redirect(target);
    }

    String library(LayoutTarget target, int section, String region, Model model) {
        regionOf(sectionOf(target, section), region);
        model.addAttribute("title", "Add a block");
        model.addAttribute("description", "Choose the block to place in the " + region + " region.");
        model.addAttribute("definitions", blocks.layoutDefinitions(target.entityTypeId(), target.bundle()));
        model.addAttribute("addPath", target.path() + "/section/" + section + "/region/" + region + "/add/");
        model.addAttribute("cancelPath", target.path());
        return "admin/layout-library";
    }

    String addBlockForm(LayoutTarget target, int section, String region, String plugin, Model model) {
        regionOf(sectionOf(target, section), region);
        BlockInstance blank = blankBlock(target, plugin);
        return formPage("Add " + blank.label(), addPath(target, section, region, plugin),
                blockForm(blank, target.path()), Map.of(), model);
    }

    String addBlock(LayoutTarget target, int section, String region, String plugin, Map<String, String> submitted,
            Model model) {
        Section holder = sectionOf(target, section);
        regionOf(holder, region);
        BlockInstance blank = blankBlock(target, plugin);

        FormState state = validated(blank, submitted, target.path());
        if (state.hasErrors()) {
            return formPage("Add " + blank.label(), addPath(target, section, region, plugin),
                    blockForm(submittedBlock(blank, submitted), target.path()), state.errors(), model);
        }
        SectionComponent added = new SectionComponent(region, holder.nextWeight(region),
                submittedBlock(blank, submitted));
        target.save(replaced(target, section, holder.withComponent(added)));
        return redirect(target);
    }

    String configureForm(LayoutTarget target, String block, Model model) {
        SectionComponent component = configurableComponentOf(target, block);
        return formPage("Configure " + component.block().label(), target.path() + "/block/" + block,
                blockForm(component.block(), target.path()), Map.of(), model);
    }

    String configure(LayoutTarget target, String block, Map<String, String> submitted, Model model) {
        SectionComponent component = configurableComponentOf(target, block);
        FormState state = validated(component.block(), submitted, target.path());
        if (state.hasErrors()) {
            return formPage("Configure " + component.block().label(), target.path() + "/block/" + block,
                    blockForm(submittedBlock(component.block(), submitted), target.path()), state.errors(), model);
        }
        int index = sectionHolding(target, block).orElseThrow();
        Section holder = target.sections().get(index);
        target.save(replaced(target, index,
                holder.replacing(component.withBlock(submittedBlock(component.block(), submitted)))));
        return redirect(target);
    }

    /** A block whose plugin the site no longer has can still be removed. */
    String removeBlock(LayoutTarget target, String block) {
        componentOf(target, block);
        int index = sectionHolding(target, block).orElseThrow();
        target.save(replaced(target, index, target.sections().get(index).withoutComponent(block)));
        return redirect(target);
    }

    private Section arranged(Section section, int index, Map<String, String> submitted) {
        List<String> regionIds = regionsOf(section).stream().map(LayoutRegion::id).toList();
        Section arranged = section;
        String widths = submitted.getOrDefault(WIDTHS_PREFIX + index, "");
        if (layouts.has(section.layout()) && layouts.plugin(section.layout()).columnWidths().contains(widths)) {
            arranged = arranged.withColumnWidths(widths);
        }
        for (SectionComponent component : section.components()) {
            SectionComponent moved = component;
            String region = submitted.getOrDefault(REGION_PREFIX + component.block().id(), "");
            if (regionIds.contains(region)) {
                moved = moved.inRegion(region);
            }
            String weight = submitted.getOrDefault(WEIGHT_PREFIX + component.block().id(), "");
            if (weight.matches(BlockLayoutController.WHOLE_NUMBER)) {
                moved = moved.withWeight(Integer.parseInt(weight));
            }
            arranged = arranged.replacing(moved);
        }
        return arranged;
    }

    private static List<Section> replaced(LayoutTarget target, int index, Section replacement) {
        List<Section> changed = new ArrayList<>(target.sections());
        changed.set(index, replacement);
        return changed;
    }

    private FormState validated(BlockInstance block, Map<String, String> submitted, String cancelPath) {
        FormState state = forms.validate(blockForm(block, cancelPath), new LinkedHashMap<>(submitted));
        blocks.plugin(block.plugin()).validateSettings(submitted).forEach(state::error);
        return state;
    }

    private BlockInstance submittedBlock(BlockInstance base, Map<String, String> submitted) {
        return new BlockInstance(base.id(), base.plugin(), submitted.getOrDefault(LABEL, ""),
                submitted.containsKey(LABEL_DISPLAY), blocks.plugin(base.plugin()).settingsValues(submitted));
    }

    /**
     * A block about to be placed. A field block's title starts hidden, since the
     * field it draws usually says what it is.
     */
    private BlockInstance blankBlock(LayoutTarget target, String plugin) {
        boolean offered = blocks.layoutDefinitions(target.entityTypeId(), target.bundle()).stream()
                .anyMatch(definition -> definition.id().equals(plugin));
        if (!offered) {
            throw new EntityNotFoundException("block plugin", plugin);
        }
        BlockInstance block = BlockInstance.of(UUID.randomUUID().toString(), plugin, blocks.plugin(plugin).label());
        return plugin.startsWith(FieldBlockDeriver.ID + ":") ? block.withoutLabel() : block;
    }

    private FormElement blockForm(BlockInstance block, String cancelPath) {
        FormElement form = FormElement.of(ElementType.CONTAINER, "block")
                .child(FormElement.of(ElementType.TEXTFIELD, LABEL)
                        .label("Title")
                        .markRequired()
                        .value(block.label())
                        .rule(ValidationRule.maxLength(255)))
                .child(FormElement.of(ElementType.CHECKBOX, LABEL_DISPLAY)
                        .label("Display title")
                        .value(block.labelDisplay()));

        BlockPlugin plugin = blocks.plugin(block.plugin());
        List<FormElement> settings = plugin.settingsForm(block.settings());
        if (!settings.isEmpty()) {
            FormElement fieldset = FormElement.of(ElementType.FIELDSET, "settings").label("Block settings");
            settings.forEach(fieldset::child);
            form.child(fieldset);
        }
        return form.child(FormElement.of(ElementType.ACTIONS, "actions")
                .child(FormElement.of(ElementType.SUBMIT, "save").label("Save block"))
                .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(cancelPath)));
    }

    private String formPage(
            String title, String action, FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", title);
        model.addAttribute("description", "What the block shows in this layout.");
        model.addAttribute("action", action);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/block-form";
    }

    private List<LayoutEditorSection> editorSections(List<Section> sections) {
        List<LayoutEditorSection> editorSections = new ArrayList<>();
        for (int index = 0; index < sections.size(); index++) {
            Section section = sections.get(index);
            boolean known = layouts.has(section.layout());
            List<LayoutRegion> regions = regionsOf(section);
            List<String> offered = known ? layouts.plugin(section.layout()).columnWidths() : List.of();
            String widths = offered.contains(section.columnWidths()) || offered.isEmpty()
                    ? section.columnWidths() : offered.getFirst();
            editorSections.add(new LayoutEditorSection(
                    index,
                    known ? layouts.plugin(section.layout()).label() : section.layout() + " (missing)",
                    offered.stream().map(option -> new SelectOption(option, option)).toList(),
                    widths,
                    regions,
                    regions.stream()
                            .map(region -> new LayoutEditorRegion(region.id(), region.label(),
                                    section.inRegion(region.id()).stream().map(this::row).toList()))
                            .toList()));
        }
        return editorSections;
    }

    private LayoutEditorRow row(SectionComponent component) {
        String plugin = component.block().plugin();
        String blockLabel = blocks.has(plugin) ? blocks.plugin(plugin).label() : plugin + " (missing)";
        return new LayoutEditorRow(component.block().id(), component.block().label(), blockLabel,
                component.region(), component.weight());
    }

    private List<LayoutRegion> regionsOf(Section section) {
        return layouts.has(section.layout()) ? layouts.plugin(section.layout()).regions() : List.of();
    }

    private static String addPath(LayoutTarget target, int section, String region, String plugin) {
        return target.path() + "/section/" + section + "/region/" + region + "/add/" + plugin;
    }

    private static String redirect(LayoutTarget target) {
        return "redirect:" + target.path();
    }

    private static Section sectionOf(LayoutTarget target, int index) {
        if (index < 0 || index >= target.sections().size()) {
            throw new EntityNotFoundException("section", String.valueOf(index));
        }
        return target.sections().get(index);
    }

    private void regionOf(Section section, String region) {
        if (regionsOf(section).stream().noneMatch(candidate -> candidate.id().equals(region))) {
            throw new EntityNotFoundException("region", region);
        }
    }

    private static Optional<Integer> sectionHolding(LayoutTarget target, String blockId) {
        for (int index = 0; index < target.sections().size(); index++) {
            if (target.sections().get(index).component(blockId).isPresent()) {
                return Optional.of(index);
            }
        }
        return Optional.empty();
    }

    private static SectionComponent componentOf(LayoutTarget target, String blockId) {
        return sectionHolding(target, blockId)
                .flatMap(index -> target.sections().get(index).component(blockId))
                .orElseThrow(() -> new EntityNotFoundException("block", blockId));
    }

    private SectionComponent configurableComponentOf(LayoutTarget target, String blockId) {
        SectionComponent component = componentOf(target, blockId);
        if (!blocks.has(component.block().plugin())) {
            throw new EntityNotFoundException("block plugin", component.block().plugin());
        }
        return component;
    }
}
