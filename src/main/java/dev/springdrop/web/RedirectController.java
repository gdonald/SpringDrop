package dev.springdrop.web;

import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.path.PathAliasManager;
import dev.springdrop.kernel.path.Redirect;
import dev.springdrop.kernel.path.RedirectManager;
import dev.springdrop.kernel.web.EntityNotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The site's redirects: from which path, to where, and with which status. */
@Controller
public class RedirectController {

    public static final String PATH = "/admin/config/search/redirect";

    public static final String ADMINISTER_REDIRECTS = "administer redirects";

    public static final String SOURCE = "source";

    public static final String DESTINATION = "destination";

    public static final String STATUS = "status";

    private final RedirectManager redirects;
    private final FormBuilder formBuilder;
    private final FormRenderer renderer;

    public RedirectController(RedirectManager redirects, FormBuilder formBuilder, FormRenderer renderer) {
        this.redirects = redirects;
        this.formBuilder = formBuilder;
        this.renderer = renderer;
    }

    @GetMapping(PATH)
    public String list(Model model) {
        model.addAttribute("title", "Redirects");
        model.addAttribute("description", "Paths that send the browser elsewhere.");
        model.addAttribute("rows", redirects.all());
        model.addAttribute("addPath", PATH + "/add");
        model.addAttribute("managePath", PATH + "/manage/");
        return "admin/redirects";
    }

    @GetMapping(PATH + "/add")
    public String addForm(Model model) {
        return formPage("Add redirect", PATH + "/add",
                redirectForm("", "", String.valueOf(RedirectManager.MOVED_PERMANENTLY)), Map.of(), model);
    }

    @PostMapping(PATH + "/add")
    public String add(@RequestParam Map<String, String> submitted, Model model) {
        return save(null, submitted, "Add redirect", PATH + "/add", model);
    }

    @GetMapping(PATH + "/manage/{id}")
    public String editForm(@PathVariable long id, Model model) {
        Redirect redirect = redirectOf(id);
        return formPage("Edit redirect", PATH + "/manage/" + id, redirectForm(redirect.source(),
                redirect.destination(), String.valueOf(redirect.status())), Map.of(), model);
    }

    @PostMapping(PATH + "/manage/{id}")
    public String edit(@PathVariable long id, @RequestParam Map<String, String> submitted, Model model) {
        redirectOf(id);
        return save(id, submitted, "Edit redirect", PATH + "/manage/" + id, model);
    }

    private String save(Long id, Map<String, String> submitted, String title, String action, Model model) {
        String source = submitted.getOrDefault(SOURCE, "").strip();
        String destination = submitted.getOrDefault(DESTINATION, "").strip();
        String status = submitted.getOrDefault(STATUS, "");
        FormElement tree = redirectForm(source, destination, status);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        int code = status.matches("\\d{3}") ? Integer.parseInt(status) : 0;
        if (!state.hasErrors()) {
            redirects.refusal(id, source, destination, code).ifPresent(reason -> state.error(fieldOf(reason), reason));
        }
        if (state.hasErrors()) {
            return formPage(title, action, tree, state.errors(), model);
        }
        redirects.save(id, source, destination, code);
        return "redirect:" + PATH;
    }

    /** The field a refusal is about. */
    private static String fieldOf(String reason) {
        if (reason.equals(RedirectManager.STATUS_MESSAGE)) {
            return STATUS;
        }
        return reason.equals(RedirectManager.SELF_MESSAGE) ? DESTINATION : SOURCE;
    }

    private static FormElement redirectForm(String source, String destination, String status) {
        return FormElement.of(ElementType.CONTAINER, "redirect")
                .child(FormElement.of(ElementType.TEXTFIELD, SOURCE).label("From").markRequired().value(source)
                        .description("A path of the site, such as /old-page.")
                        .rule(ValidationRule.maxLength(PathAliasManager.MAX_LENGTH))
                        .rule(ValidationRule.pattern(PathAliasManager.PATTERN)
                                .withMessage(PathAliasManager.PATTERN_MESSAGE)))
                .child(FormElement.of(ElementType.TEXTFIELD, DESTINATION).label("To").markRequired()
                        .value(destination)
                        .description("A path of the site, such as /about, or an address such as https://example.com/.")
                        .rule(ValidationRule.maxLength(RedirectManager.MAX_DESTINATION_LENGTH))
                        .rule(ValidationRule.pattern(RedirectManager.DESTINATION_PATTERN)
                                .withMessage(RedirectManager.DESTINATION_MESSAGE)))
                .child(FormElement.of(ElementType.SELECT, STATUS).label("Status").markRequired().value(status)
                        .options(RedirectManager.STATUSES.stream()
                                .map(code -> new SelectOption(String.valueOf(code), statusLabel(code))).toList()))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH)));
    }

    public static String statusLabel(int code) {
        return switch (code) {
            case 301 -> "301 Moved permanently";
            case 302 -> "302 Found";
            case 303 -> "303 See other";
            case 307 -> "307 Temporary redirect";
            default -> "308 Permanent redirect";
        };
    }

    @GetMapping(PATH + "/manage/{id}/delete")
    public String confirmDelete(@PathVariable long id, Model model) {
        Redirect redirect = redirectOf(id);
        model.addAttribute("title", "Delete the redirect from " + redirect.source() + "?");
        model.addAttribute("description", "The path answers as it would without it. This action cannot be undone.");
        model.addAttribute("action", PATH + "/manage/" + id + "/delete");
        model.addAttribute("formMarkup", renderer.render(FormElement.of(ElementType.CONTAINER, "confirm")
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "confirm").label("Delete redirect")
                                .attribute("class", "btn btn-danger"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH)))));
        return "admin/block-form";
    }

    @PostMapping(PATH + "/manage/{id}/delete")
    public String delete(@PathVariable long id) {
        redirects.delete(redirectOf(id).id());
        return "redirect:" + PATH;
    }

    private String formPage(String title, String action, FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", title);
        model.addAttribute("description", "A request for the path is sent to the destination before anything else "
                + "answers it.");
        model.addAttribute("action", action);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/block-form";
    }

    private Redirect redirectOf(long id) {
        return redirects.find(id).orElseThrow(() -> new EntityNotFoundException("redirect", String.valueOf(id)));
    }
}
