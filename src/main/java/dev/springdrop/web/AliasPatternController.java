package dev.springdrop.web;

import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeType;
import dev.springdrop.kernel.node.NodeTypeManager;
import dev.springdrop.kernel.path.AliasPattern;
import dev.springdrop.kernel.path.AliasPatternManager;
import dev.springdrop.kernel.path.PathAliasManager;
import dev.springdrop.kernel.web.EntityNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The patterns content types' URL aliases are made from, one per content type.
 * A pattern is text and tokens, such as {@code /blog/[node:title]}.
 */
@Controller
public class AliasPatternController {

    public static final String PATH = "/admin/config/search/path/patterns";

    public static final String ADMINISTER_URL_ALIASES = "administer url aliases";

    public static final String BUNDLE = "bundle";

    public static final String PATTERN = "pattern";

    public static final String REGENERATE = "regenerate";

    /** What a pattern is written as: a slash, then alias characters and tokens. */
    public static final String PATTERN_RULE = "/([A-Za-z0-9/_.~-]|\\[[a-z0-9_]+:[a-z0-9_:.\\-]+])*";

    private final AliasPatternManager patterns;
    private final NodeTypeManager nodeTypes;
    private final FormBuilder formBuilder;
    private final FormRenderer renderer;

    public AliasPatternController(AliasPatternManager patterns, NodeTypeManager nodeTypes, FormBuilder formBuilder,
            FormRenderer renderer) {
        this.patterns = patterns;
        this.nodeTypes = nodeTypes;
        this.formBuilder = formBuilder;
        this.renderer = renderer;
    }

    public record PatternRow(String bundle, String label, String pattern, boolean regenerate) {
    }

    @GetMapping(PATH)
    public String list(Model model) {
        model.addAttribute("title", "URL alias patterns");
        model.addAttribute("description", "How the URL aliases of each content type's pages are made.");
        model.addAttribute("rows", patterns.all().stream()
                .filter(pattern -> pattern.entityType().equals(NodeEntityType.ID))
                .map(pattern -> new PatternRow(pattern.bundle(), nodeTypes.find(pattern.bundle())
                        .map(NodeType::label).orElse(pattern.bundle()), pattern.pattern(), pattern.regenerate()))
                .toList());
        model.addAttribute("addPath", PATH + "/add");
        model.addAttribute("managePath", PATH + "/manage/");
        return "admin/alias-patterns";
    }

    @GetMapping(PATH + "/add")
    public String addForm(Model model) {
        return formPage("Add URL alias pattern", PATH + "/add", patternForm("", "", false, true), Map.of(), model);
    }

    @PostMapping(PATH + "/add")
    public String add(@RequestParam Map<String, String> submitted, Model model) {
        String bundle = submitted.getOrDefault(BUNDLE, "");
        FormElement tree = patternForm(bundle, submitted.getOrDefault(PATTERN, ""),
                submitted.containsKey(REGENERATE), true);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        if (!bundle.isEmpty() && nodeTypes.find(bundle).isEmpty()) {
            state.error(BUNDLE, "Choose a content type.");
        }
        return save(bundle, submitted, tree, state, "Add URL alias pattern", PATH + "/add", model);
    }

    @GetMapping(PATH + "/manage/{bundle}")
    public String editForm(@PathVariable String bundle, Model model) {
        AliasPattern pattern = patternOf(bundle);
        return formPage("Edit URL alias pattern", PATH + "/manage/" + bundle,
                patternForm(bundle, pattern.pattern(), pattern.regenerate(), false), Map.of(), model);
    }

    @PostMapping(PATH + "/manage/{bundle}")
    public String edit(@PathVariable String bundle, @RequestParam Map<String, String> submitted, Model model) {
        patternOf(bundle);
        FormElement tree = patternForm(bundle, submitted.getOrDefault(PATTERN, ""),
                submitted.containsKey(REGENERATE), false);
        return save(bundle, submitted, tree, formBuilder.validate(tree, new LinkedHashMap<>(submitted)),
                "Edit URL alias pattern", PATH + "/manage/" + bundle, model);
    }

    private String save(String bundle, Map<String, String> submitted, FormElement tree, FormState state,
            String title, String action, Model model) {
        if (state.hasErrors()) {
            return formPage(title, action, tree, state.errors(), model);
        }
        patterns.save(new AliasPattern(NodeEntityType.ID, bundle, submitted.getOrDefault(PATTERN, "").strip(),
                submitted.containsKey(REGENERATE)));
        return "redirect:" + PATH;
    }

    private FormElement patternForm(String bundle, String pattern, boolean regenerate, boolean adding) {
        FormElement form = FormElement.of(ElementType.CONTAINER, "alias-pattern");
        if (adding) {
            form.child(FormElement.of(ElementType.SELECT, BUNDLE).label("Content type").markRequired().value(bundle)
                    .options(nodeTypes.all().stream().map(type -> new SelectOption(type.id(), type.label()))
                            .toList()));
        } else {
            form.child(FormElement.of(ElementType.TEXT, BUNDLE).label("Content type: "
                    + nodeTypes.find(bundle).map(NodeType::label).orElse(bundle)));
        }
        return form
                .child(FormElement.of(ElementType.TEXTFIELD, PATTERN).label("Pattern").markRequired().value(pattern)
                        .description("Text and tokens, such as /blog/[node:title]. Tokens: [node:title], "
                                + "[node:id], [node:type], [node:created:year].")
                        .rule(ValidationRule.maxLength(PathAliasManager.MAX_LENGTH))
                        .rule(ValidationRule.pattern(PATTERN_RULE).withMessage(
                                "Start the pattern with /, and use letters, digits, / _ . ~ - and tokens.")))
                .child(FormElement.of(ElementType.CHECKBOX, REGENERATE)
                        .label("Make the alias again each time the page is saved").value(regenerate))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH)));
    }

    @GetMapping(PATH + "/manage/{bundle}/delete")
    public String confirmDelete(@PathVariable String bundle, Model model) {
        patternOf(bundle);
        model.addAttribute("title", "Delete the URL alias pattern of "
                + nodeTypes.find(bundle).map(NodeType::label).orElse(bundle) + "?");
        model.addAttribute("description", "Aliases made from it stay. New pages get no alias of their own.");
        model.addAttribute("action", PATH + "/manage/" + bundle + "/delete");
        model.addAttribute("formMarkup", renderer.render(FormElement.of(ElementType.CONTAINER, "confirm")
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "confirm").label("Delete pattern")
                                .attribute("class", "btn btn-danger"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH)))));
        return "admin/block-form";
    }

    @PostMapping(PATH + "/manage/{bundle}/delete")
    public String delete(@PathVariable String bundle) {
        patternOf(bundle);
        patterns.delete(NodeEntityType.ID, bundle);
        return "redirect:" + PATH;
    }

    private String formPage(String title, String action, FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", title);
        model.addAttribute("description", "Tokens are replaced with the page's values, made fit for a path.");
        model.addAttribute("action", action);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/block-form";
    }

    private AliasPattern patternOf(String bundle) {
        return patterns.find(NodeEntityType.ID, bundle)
                .orElseThrow(() -> new EntityNotFoundException("URL alias pattern", bundle));
    }
}
