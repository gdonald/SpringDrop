package dev.springdrop.web;

import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.image.ImageStyle;
import dev.springdrop.kernel.image.ImageStyleManager;
import dev.springdrop.kernel.image.ResponsiveImageMapping;
import dev.springdrop.kernel.image.ResponsiveImageStyle;
import dev.springdrop.kernel.image.ResponsiveImageStyleManager;
import dev.springdrop.kernel.text.MachineNameGenerator;
import dev.springdrop.kernel.theme.Breakpoint;
import dev.springdrop.kernel.theme.BreakpointGroup;
import dev.springdrop.kernel.theme.BreakpointManager;
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
 * The site's responsive image styles: the breakpoint group each follows, the
 * image style drawn where no breakpoint applies, and for each breakpoint and
 * pixel density, a single image style or several offered by width.
 */
@Controller
public class ResponsiveImageStyleController {

    public static final String PATH = "/admin/config/media/responsive-image-style";

    public static final String ADMINISTER_RESPONSIVE_IMAGES = "administer responsive images";

    public static final String LABEL = "label";

    public static final String BREAKPOINT_GROUP = "breakpoint_group";

    public static final String FALLBACK = "fallback_image_style";

    public static final String TYPE = "type";

    public static final String IMAGE_STYLE = "image_style";

    public static final String SIZES = "sizes";

    /** Follows a mapping's prefix in the name of each image style's box, followed by the style's id. */
    public static final String SIZES_STYLE = "sizes_style_";

    private static final String ONE_TIMES = "1x";

    private final ResponsiveImageStyleManager responsiveStyles;
    private final ImageStyleManager imageStyles;
    private final BreakpointManager breakpoints;
    private final MachineNameGenerator machineNames;
    private final FormBuilder formBuilder;
    private final FormRenderer renderer;

    public ResponsiveImageStyleController(ResponsiveImageStyleManager responsiveStyles, ImageStyleManager imageStyles,
            BreakpointManager breakpoints, MachineNameGenerator machineNames, FormBuilder formBuilder,
            FormRenderer renderer) {
        this.responsiveStyles = responsiveStyles;
        this.imageStyles = imageStyles;
        this.breakpoints = breakpoints;
        this.machineNames = machineNames;
        this.formBuilder = formBuilder;
        this.renderer = renderer;
    }

    public static String managePath(String id) {
        return PATH + "/manage/" + id;
    }

    /** The prefix of every element of one breakpoint's mapping at one pixel density. */
    public static String mappingPrefix(String breakpointId, String multiplier) {
        return "mapping_" + (breakpointId + "_" + multiplier).replaceAll("[^A-Za-z0-9]", "_") + "_";
    }

    public record ResponsiveStyleRow(String id, String label, String breakpointGroup) {
    }

    @GetMapping(PATH)
    public String list(Model model) {
        model.addAttribute("title", "Responsive image styles");
        model.addAttribute("description", "Images drawn at a fitting size for each breakpoint of a theme.");
        model.addAttribute("rows", responsiveStyles.all().stream()
                .map(style -> new ResponsiveStyleRow(style.id(), style.label(),
                        breakpoints.group(style.breakpointGroup()).map(BreakpointGroup::label)
                                .orElse(style.breakpointGroup())))
                .toList());
        model.addAttribute("addPath", PATH + "/add");
        model.addAttribute("managePath", PATH + "/manage/");
        return "admin/responsive-image-styles";
    }

    @GetMapping(PATH + "/add")
    public String addForm(Model model) {
        return formPage("Add responsive image style", PATH + "/add", addingForm("", "", ""), Map.of(), model);
    }

    @PostMapping(PATH + "/add")
    public String add(@RequestParam Map<String, String> submitted, Model model) {
        String label = submitted.getOrDefault(LABEL, "").strip();
        String group = submitted.getOrDefault(BREAKPOINT_GROUP, "");
        String fallback = submitted.getOrDefault(FALLBACK, "");
        FormElement tree = addingForm(label, group, fallback);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        if (breakpoints.group(group).isEmpty()) {
            state.error(BREAKPOINT_GROUP, "Choose a breakpoint group.");
        }
        if (state.hasErrors()) {
            return formPage("Add responsive image style", PATH + "/add", tree, state.errors(), model);
        }
        String id = machineNames.generateUnique(label, taken -> responsiveStyles.find(taken).isPresent());
        responsiveStyles.save(new ResponsiveImageStyle(id, label, group, fallbackOf(fallback), List.of()));
        return "redirect:" + managePath(id);
    }

    private FormElement addingForm(String label, String group, String fallback) {
        return FormElement.of(ElementType.CONTAINER, "responsive-image-style")
                .child(labelElement(label))
                .child(FormElement.of(ElementType.SELECT, BREAKPOINT_GROUP).label("Breakpoint group").markRequired()
                        .value(group)
                        .options(breakpoints.groups().stream()
                                .map(each -> new SelectOption(each.id(), each.label())).toList()))
                .child(fallbackElement(fallback))
                .child(actions("Create responsive image style"));
    }

    @GetMapping(PATH + "/manage/{id}")
    public String editForm(@PathVariable String id, Model model) {
        ResponsiveImageStyle style = styleOf(id);
        return formPage("Edit " + style.label(), managePath(id), styleForm(style), Map.of(), model);
    }

    @PostMapping(PATH + "/manage/{id}")
    public String edit(@PathVariable String id, @RequestParam Map<String, String> submitted, Model model) {
        ResponsiveImageStyle style = styleOf(id);
        ResponsiveImageStyle candidate = new ResponsiveImageStyle(style.id(),
                submitted.getOrDefault(LABEL, "").strip(), style.breakpointGroup(),
                fallbackOf(submitted.getOrDefault(FALLBACK, "")), mappings(style.breakpointGroup(), submitted));
        FormElement tree = styleForm(candidate);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        if (state.hasErrors()) {
            return formPage("Edit " + style.label(), managePath(id), tree, state.errors(), model);
        }
        responsiveStyles.save(candidate);
        return "redirect:" + PATH;
    }

    /**
     * The mappings a submission gives. A sizes mapping with no image style
     * ticked offers the browser nothing, so it is left out.
     */
    private List<ResponsiveImageMapping> mappings(String groupId, Map<String, String> submitted) {
        List<ResponsiveImageMapping> mappings = new ArrayList<>();
        for (Breakpoint breakpoint : groupOf(groupId).breakpoints()) {
            for (String multiplier : breakpoint.multipliers()) {
                String prefix = mappingPrefix(breakpoint.id(), multiplier);
                String type = submitted.getOrDefault(prefix + TYPE, "");
                String single = submitted.getOrDefault(prefix + IMAGE_STYLE, "");
                if (type.equals(ResponsiveImageMapping.IMAGE_STYLE) && styleChoice(single)) {
                    mappings.add(ResponsiveImageMapping.single(breakpoint.id(), multiplier, single));
                } else if (type.equals(ResponsiveImageMapping.SIZES) && multiplier.equals(ONE_TIMES)) {
                    List<String> ticked = imageStyles.all().stream().map(ImageStyle::id)
                            .filter(styleId -> submitted.containsKey(prefix + SIZES_STYLE + styleId))
                            .toList();
                    if (!ticked.isEmpty()) {
                        mappings.add(ResponsiveImageMapping.bySize(breakpoint.id(),
                                submitted.getOrDefault(prefix + SIZES, "").strip(), ticked));
                    }
                }
            }
        }
        return mappings;
    }

    private FormElement styleForm(ResponsiveImageStyle style) {
        FormElement form = FormElement.of(ElementType.CONTAINER, "responsive-image-style")
                .child(labelElement(style.label()))
                .child(FormElement.of(ElementType.TEXT, BREAKPOINT_GROUP)
                        .label("Breakpoint group: " + groupOf(style.breakpointGroup()).label()))
                .child(fallbackElement(style.fallbackImageStyle()));
        for (Breakpoint breakpoint : groupOf(style.breakpointGroup()).breakpoints()) {
            for (String multiplier : breakpoint.multipliers()) {
                form.child(mappingElement(style, breakpoint, multiplier));
            }
        }
        return form.child(actions("Save"));
    }

    private FormElement mappingElement(ResponsiveImageStyle style, Breakpoint breakpoint, String multiplier) {
        String prefix = mappingPrefix(breakpoint.id(), multiplier);
        Optional<ResponsiveImageMapping> mapping = style.mappingsFor(breakpoint.id()).stream()
                .filter(each -> each.multiplier().equals(multiplier)).findFirst();
        List<SelectOption> types = new ArrayList<>(List.of(new SelectOption("", "Not drawn at this breakpoint"),
                new SelectOption(ResponsiveImageMapping.IMAGE_STYLE, "A single image style")));
        if (multiplier.equals(ONE_TIMES)) {
            types.add(new SelectOption(ResponsiveImageMapping.SIZES, "Several image styles, chosen by width"));
        }
        String type = mapping.map(ResponsiveImageMapping::type).orElse("");
        FormElement details = FormElement.of(ElementType.DETAILS, prefix + "details")
                .label(breakpoint.label() + " " + multiplier + " " + breakpoint.mediaQuery())
                .child(FormElement.of(ElementType.SELECT, prefix + TYPE).label("Type").value(type).options(types))
                .child(FormElement.of(ElementType.SELECT, prefix + IMAGE_STYLE).label("Image style")
                        .value(mapping.map(ResponsiveImageMapping::imageStyle).orElse(""))
                        .options(styleOptions())
                        .visibleWhen(prefix + TYPE, ResponsiveImageMapping.IMAGE_STYLE)
                        .requiredWhen(prefix + TYPE, ResponsiveImageMapping.IMAGE_STYLE));
        if (!type.isEmpty()) {
            details.attribute("open", "open");
        }
        if (multiplier.equals(ONE_TIMES)) {
            details.child(FormElement.of(ElementType.TEXTFIELD, prefix + SIZES).label("Sizes")
                    .description("How wide the image shows, such as (min-width: 768px) 50vw, 100vw.")
                    .value(mapping.map(ResponsiveImageMapping::sizes).orElse(""))
                    .rule(ValidationRule.maxLength(255))
                    .visibleWhen(prefix + TYPE, ResponsiveImageMapping.SIZES)
                    .requiredWhen(prefix + TYPE, ResponsiveImageMapping.SIZES));
            List<String> ticked = mapping.map(ResponsiveImageMapping::sizesImageStyles).orElse(List.of());
            for (ImageStyle imageStyle : imageStyles.all()) {
                details.child(FormElement.of(ElementType.CHECKBOX, prefix + SIZES_STYLE + imageStyle.id())
                        .label(imageStyle.label())
                        .value(ticked.contains(imageStyle.id()))
                        .visibleWhen(prefix + TYPE, ResponsiveImageMapping.SIZES));
            }
        }
        return details;
    }

    private List<SelectOption> styleOptions() {
        List<SelectOption> options = new ArrayList<>(List.of(new SelectOption(ResponsiveImageStyle.ORIGINAL,
                "None (original image)")));
        imageStyles.all().forEach(style -> options.add(new SelectOption(style.id(), style.label())));
        return options;
    }

    private boolean styleChoice(String choice) {
        return choice.equals(ResponsiveImageStyle.ORIGINAL) || imageStyles.find(choice).isPresent();
    }

    private String fallbackOf(String choice) {
        return styleChoice(choice) ? choice : ResponsiveImageStyle.ORIGINAL;
    }

    private static FormElement labelElement(String label) {
        return FormElement.of(ElementType.TEXTFIELD, LABEL).label("Name").markRequired().value(label)
                .rule(ValidationRule.maxLength(255));
    }

    private FormElement fallbackElement(String fallback) {
        return FormElement.of(ElementType.SELECT, FALLBACK).label("Fallback image style").markRequired()
                .description("Drawn by browsers that take none of the sources.")
                .value(fallback)
                .options(styleOptions());
    }

    private static FormElement actions(String submitLabel) {
        return FormElement.of(ElementType.ACTIONS, "actions")
                .child(FormElement.of(ElementType.SUBMIT, "save").label(submitLabel))
                .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH));
    }

    @GetMapping(PATH + "/manage/{id}/delete")
    public String confirmDelete(@PathVariable String id, Model model) {
        ResponsiveImageStyle style = styleOf(id);
        FormElement confirm = FormElement.of(ElementType.CONTAINER, "confirm")
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "confirm").label("Delete responsive image style")
                                .attribute("class", "btn btn-danger"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH)));
        model.addAttribute("title", "Delete the responsive image style " + style.label() + "?");
        model.addAttribute("description", "Images shown in it are shown as the original. This action cannot be undone.");
        model.addAttribute("action", managePath(id) + "/delete");
        model.addAttribute("formMarkup", renderer.render(confirm));
        return "admin/block-form";
    }

    @PostMapping(PATH + "/manage/{id}/delete")
    public String delete(@PathVariable String id) {
        responsiveStyles.delete(styleOf(id).id());
        return "redirect:" + PATH;
    }

    private String formPage(String title, String action, FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", title);
        model.addAttribute("description", "The image style drawn at each breakpoint, widest breakpoint first "
                + "in the page, narrowest first here.");
        model.addAttribute("action", action);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/block-form";
    }

    private ResponsiveImageStyle styleOf(String id) {
        return responsiveStyles.find(id).orElseThrow(() -> new EntityNotFoundException("responsive image style", id));
    }

    /** The group a style follows, or an empty one when the theme declaring it is gone. */
    private BreakpointGroup groupOf(String id) {
        return breakpoints.group(id).orElseGet(() -> new BreakpointGroup(id, id, List.of()));
    }
}
