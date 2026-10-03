package dev.springdrop.web;

import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.image.EffectConfig;
import dev.springdrop.kernel.image.ImageEffect;
import dev.springdrop.kernel.image.ImageStyle;
import dev.springdrop.kernel.image.ImageStyleManager;
import dev.springdrop.kernel.plugin.PluginManager;
import dev.springdrop.kernel.text.MachineNameGenerator;
import dev.springdrop.kernel.web.EntityNotFoundException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The site's image styles: their names and the effects each applies, in order,
 * with each effect's settings. Every change to a style removes the images drawn
 * with it before, so they are drawn again the new way.
 */
@Controller
public class ImageStyleController {

    public static final String PATH = "/admin/config/media/image-styles";

    public static final String LABEL = "label";

    /** The prefix of each effect's weight, followed by the effect's id. */
    public static final String WEIGHT_PREFIX = "weight_";

    public static final String NEW_EFFECT = "new_effect";

    /** The button that saves the style and goes on to add the chosen effect. */
    public static final String ADD_EFFECT = "add_effect";

    private final ImageStyleManager styles;
    private final MachineNameGenerator machineNames;
    private final FormBuilder formBuilder;
    private final FormRenderer renderer;

    public ImageStyleController(ImageStyleManager styles, MachineNameGenerator machineNames, FormBuilder formBuilder,
            FormRenderer renderer) {
        this.styles = styles;
        this.machineNames = machineNames;
        this.formBuilder = formBuilder;
        this.renderer = renderer;
    }

    public static String managePath(String id) {
        return PATH + "/manage/" + id;
    }

    public record StyleRow(String id, String label, String effects) {
    }

    @GetMapping(PATH)
    public String list(Model model) {
        PluginManager<ImageEffect> effects = styles.effects();
        List<StyleRow> rows = styles.all().stream()
                .map(style -> new StyleRow(style.id(), style.label(), String.join(", ", style.effects().stream()
                        .filter(effect -> effects.has(effect.plugin()))
                        .map(effect -> describe(effects.get(effect.plugin()), effect))
                        .toList())))
                .toList();
        model.addAttribute("title", "Image styles");
        model.addAttribute("description", "The ways images are drawn, such as thumbnails, each a series of effects.");
        model.addAttribute("rows", rows);
        model.addAttribute("addPath", PATH + "/add");
        model.addAttribute("managePath", PATH + "/manage/");
        return "admin/image-styles";
    }

    private static String describe(ImageEffect plugin, EffectConfig effect) {
        String summary = plugin.summary(effect.settings());
        return summary.isEmpty() ? plugin.label() : plugin.label() + " " + summary;
    }

    @GetMapping(PATH + "/add")
    public String addForm(Model model) {
        return formPage("Add image style", PATH + "/add", labelForm("", "Create new style"), Map.of(), model);
    }

    @PostMapping(PATH + "/add")
    public String add(@RequestParam Map<String, String> submitted, Model model) {
        String label = submitted.getOrDefault(LABEL, "").strip();
        FormElement tree = labelForm(label, "Create new style");
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        if (state.hasErrors()) {
            return formPage("Add image style", PATH + "/add", tree, state.errors(), model);
        }
        String id = machineNames.generateUnique(label, taken -> styles.find(taken).isPresent());
        styles.save(new ImageStyle(id, label, List.of()));
        return "redirect:" + managePath(id);
    }

    private static FormElement labelForm(String label, String submitLabel) {
        return FormElement.of(ElementType.CONTAINER, "image-style")
                .child(FormElement.of(ElementType.TEXTFIELD, LABEL).label("Name").markRequired().value(label)
                        .rule(ValidationRule.maxLength(255)))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label(submitLabel))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH)));
    }

    @GetMapping(PATH + "/manage/{id}")
    public String editForm(@PathVariable String id, Model model) {
        ImageStyle style = styleOf(id);
        return formPage("Edit style " + style.label(), managePath(id), styleForm(style), Map.of(), model);
    }

    /**
     * Saves the name and the order of the effects. Pressed with the add-effect
     * button, it goes on to the chosen effect: straight into the style for an
     * effect without settings, or to its settings form.
     */
    @PostMapping(PATH + "/manage/{id}")
    public String edit(@PathVariable String id, @RequestParam Map<String, String> submitted, Model model) {
        ImageStyle style = styleOf(id);
        ImageStyle candidate = style.withLabel(submitted.getOrDefault(LABEL, "").strip());
        for (EffectConfig effect : style.effects()) {
            String weight = submitted.getOrDefault(WEIGHT_PREFIX + effect.id(), "");
            if (weight.matches(BlockLayoutController.WHOLE_NUMBER)) {
                candidate = candidate.withEffect(new EffectConfig(effect.id(), effect.plugin(),
                        Integer.parseInt(weight), effect.settings()));
            }
        }
        FormElement tree = styleForm(candidate);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        String chosen = submitted.getOrDefault(NEW_EFFECT, "");
        boolean adding = submitted.containsKey(ADD_EFFECT);
        if (adding && !styles.effects().has(chosen)) {
            state.error(NEW_EFFECT, "Choose an effect to add.");
        }
        if (state.hasErrors()) {
            return formPage("Edit style " + style.label(), managePath(id), tree, state.errors(), model);
        }
        styles.save(candidate);
        if (!adding) {
            return "redirect:" + PATH;
        }
        if (styles.effects().get(chosen).settingsForm(Map.of()).isEmpty()) {
            styles.save(candidate.withEffect(newEffect(candidate, chosen, Map.of())));
            return "redirect:" + managePath(id);
        }
        return "redirect:" + managePath(id) + "/add/" + chosen;
    }

    private FormElement styleForm(ImageStyle style) {
        PluginManager<ImageEffect> effects = styles.effects();
        FormElement effectList = FormElement.of(ElementType.FIELDSET, "effects").label("Effects");
        if (style.effects().isEmpty()) {
            effectList.child(FormElement.of(ElementType.TEXT, "no_effects")
                    .label("There are no effects yet. Images drawn with this style are copies of the original."));
        }
        for (EffectConfig effect : style.effects()) {
            boolean known = effects.has(effect.plugin());
            String effectPath = managePath(style.id()) + "/effects/" + effect.id();
            FormElement row = FormElement.of(ElementType.CONTAINER, "effect_" + effect.id())
                    .attribute("class", "d-flex flex-wrap align-items-end gap-2 mb-3")
                    .child(FormElement.of(ElementType.TEXT, "effect_label_" + effect.id())
                            .label(known ? describe(effects.get(effect.plugin()), effect)
                                    : "Missing effect " + effect.plugin())
                            .attribute("class", "me-auto mb-0"))
                    .child(FormElement.of(ElementType.NUMBER, WEIGHT_PREFIX + effect.id()).label("Weight")
                            .value(effect.weight())
                            .rule(ValidationRule.pattern(BlockLayoutController.WHOLE_NUMBER)
                                    .withMessage("The weight is a whole number.")));
            if (known && !effects.get(effect.plugin()).settingsForm(effect.settings()).isEmpty()) {
                row.child(FormElement.of(ElementType.LINK, "edit_" + effect.id()).label("Edit").value(effectPath)
                        .attribute("class", "btn btn-secondary btn-sm"));
            }
            effectList.child(row.child(FormElement.of(ElementType.LINK, "remove_" + effect.id()).label("Remove")
                    .value(effectPath + "/delete").attribute("class", "btn btn-danger btn-sm")));
        }
        List<SelectOption> choices = effects.ids().stream()
                .map(effectId -> new SelectOption(effectId, effects.get(effectId).label()))
                .sorted(Comparator.comparing(SelectOption::label))
                .toList();
        effectList.child(FormElement.of(ElementType.SELECT, NEW_EFFECT).label("New effect").options(choices))
                .child(FormElement.of(ElementType.SUBMIT, ADD_EFFECT).label("Add effect")
                        .attribute("class", "btn btn-secondary"));
        return FormElement.of(ElementType.CONTAINER, "image-style")
                .child(FormElement.of(ElementType.TEXTFIELD, LABEL).label("Name").markRequired().value(style.label())
                        .rule(ValidationRule.maxLength(255)))
                .child(effectList)
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save"))
                        .child(FormElement.of(ElementType.LINK, "flush").label("Flush images")
                                .value(managePath(style.id()) + "/flush"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH)));
    }

    @GetMapping(PATH + "/manage/{id}/add/{plugin}")
    public String addEffectForm(@PathVariable String id, @PathVariable String plugin, Model model) {
        ImageStyle style = styleOf(id);
        ImageEffect effect = effectOf(plugin);
        return formPage("Add " + effect.label() + " to " + style.label(), managePath(id) + "/add/" + plugin,
                effectForm(style, effect, Map.of(), "Add effect"), Map.of(), model);
    }

    @PostMapping(PATH + "/manage/{id}/add/{plugin}")
    public String addEffect(@PathVariable String id, @PathVariable String plugin,
            @RequestParam Map<String, String> submitted, Model model) {
        ImageStyle style = styleOf(id);
        ImageEffect effect = effectOf(plugin);
        return saveEffect(style, effect, newEffect(style, plugin, Map.of()), submitted,
                "Add " + effect.label() + " to " + style.label(), managePath(id) + "/add/" + plugin, "Add effect",
                model);
    }

    @GetMapping(PATH + "/manage/{id}/effects/{effectId}")
    public String editEffectForm(@PathVariable String id, @PathVariable String effectId, Model model) {
        ImageStyle style = styleOf(id);
        EffectConfig config = effectConfigOf(style, effectId);
        ImageEffect effect = effectOf(config.plugin());
        return formPage("Edit " + effect.label() + " in " + style.label(),
                managePath(id) + "/effects/" + effectId, effectForm(style, effect, config.settings(), "Update effect"),
                Map.of(), model);
    }

    @PostMapping(PATH + "/manage/{id}/effects/{effectId}")
    public String editEffect(@PathVariable String id, @PathVariable String effectId,
            @RequestParam Map<String, String> submitted, Model model) {
        ImageStyle style = styleOf(id);
        EffectConfig config = effectConfigOf(style, effectId);
        ImageEffect effect = effectOf(config.plugin());
        return saveEffect(style, effect, config, submitted, "Edit " + effect.label() + " in " + style.label(),
                managePath(id) + "/effects/" + effectId, "Update effect", model);
    }

    private String saveEffect(ImageStyle style, ImageEffect effect, EffectConfig base, Map<String, String> submitted,
            String title, String action, String submitLabel, Model model) {
        FormElement tree = effectForm(style, effect, effect.settingsValues(submitted), submitLabel);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        effect.validateSettings(submitted).forEach(state::error);
        if (state.hasErrors()) {
            return formPage(title, action, tree, state.errors(), model);
        }
        styles.save(style.withEffect(new EffectConfig(base.id(), base.plugin(), base.weight(),
                effect.settingsValues(submitted))));
        return "redirect:" + managePath(style.id());
    }

    private static FormElement effectForm(ImageStyle style, ImageEffect effect, Map<String, Object> settings,
            String submitLabel) {
        FormElement form = FormElement.of(ElementType.CONTAINER, "image-effect");
        effect.settingsForm(settings).forEach(form::child);
        return form.child(FormElement.of(ElementType.ACTIONS, "actions")
                .child(FormElement.of(ElementType.SUBMIT, "save").label(submitLabel))
                .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(managePath(style.id()))));
    }

    private EffectConfig newEffect(ImageStyle style, String plugin, Map<String, Object> settings) {
        String effectId = machineNames.generateUnique(plugin, taken -> style.effect(taken).isPresent());
        int weight = style.effects().stream().mapToInt(EffectConfig::weight).max().orElse(-1) + 1;
        return new EffectConfig(effectId, plugin, weight, settings);
    }

    @GetMapping(PATH + "/manage/{id}/effects/{effectId}/delete")
    public String confirmRemoveEffect(@PathVariable String id, @PathVariable String effectId, Model model) {
        ImageStyle style = styleOf(id);
        EffectConfig effect = effectConfigOf(style, effectId);
        String label = styles.effects().has(effect.plugin()) ? styles.effects().get(effect.plugin()).label()
                : effect.plugin();
        return confirmPage("Remove the " + label + " effect from " + style.label() + "?",
                "Images drawn with this style are drawn again without it.", "Remove",
                managePath(id) + "/effects/" + effectId + "/delete", managePath(id), model);
    }

    @PostMapping(PATH + "/manage/{id}/effects/{effectId}/delete")
    public String removeEffect(@PathVariable String id, @PathVariable String effectId) {
        ImageStyle style = styleOf(id);
        effectConfigOf(style, effectId);
        styles.save(style.withoutEffect(effectId));
        return "redirect:" + managePath(id);
    }

    @GetMapping(PATH + "/manage/{id}/flush")
    public String confirmFlush(@PathVariable String id, Model model) {
        ImageStyle style = styleOf(id);
        return confirmPage("Flush the images drawn with " + style.label() + "?",
                "Each is drawn again the next time it is asked for.", "Flush", managePath(id) + "/flush",
                managePath(id), model);
    }

    @PostMapping(PATH + "/manage/{id}/flush")
    public String flush(@PathVariable String id) {
        styles.flush(styleOf(id).id());
        return "redirect:" + managePath(id);
    }

    @GetMapping(PATH + "/manage/{id}/delete")
    public String confirmDelete(@PathVariable String id, Model model) {
        ImageStyle style = styleOf(id);
        return confirmPage("Delete the image style " + style.label() + "?",
                "The images drawn with it are deleted, and anything showing images in it shows them in no style. "
                        + "This action cannot be undone.",
                "Delete image style", managePath(id) + "/delete", PATH, model);
    }

    @PostMapping(PATH + "/manage/{id}/delete")
    public String delete(@PathVariable String id) {
        styles.delete(styleOf(id).id());
        return "redirect:" + PATH;
    }

    private String confirmPage(String question, String description, String confirmLabel, String action,
            String cancelPath, Model model) {
        FormElement confirm = FormElement.of(ElementType.CONTAINER, "confirm")
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "confirm").label(confirmLabel)
                                .attribute("class", "btn btn-danger"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(cancelPath)));
        model.addAttribute("title", question);
        model.addAttribute("description", description);
        model.addAttribute("action", action);
        model.addAttribute("formMarkup", renderer.render(confirm));
        return "admin/block-form";
    }

    private String formPage(String title, String action, FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", title);
        model.addAttribute("description", "Effects apply in weight order, lightest first.");
        model.addAttribute("action", action);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/block-form";
    }

    private ImageStyle styleOf(String id) {
        return styles.find(id).orElseThrow(() -> new EntityNotFoundException("image style", id));
    }

    private ImageEffect effectOf(String plugin) {
        if (!styles.effects().has(plugin)) {
            throw new EntityNotFoundException("image effect", plugin);
        }
        return styles.effects().get(plugin);
    }

    private static EffectConfig effectConfigOf(ImageStyle style, String effectId) {
        return style.effect(effectId).orElseThrow(() -> new EntityNotFoundException("image effect", effectId));
    }
}
