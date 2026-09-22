package dev.springdrop.web;

import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.menu.MenuConfig;
import dev.springdrop.kernel.menu.MenuLink;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.menu.MenuManager;
import dev.springdrop.kernel.menu.MenuTreeBuilder;
import dev.springdrop.kernel.menu.MenuTreeItem;
import dev.springdrop.kernel.text.MachineNameGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The menus a site has and what hangs in them: adding a menu, adding links to
 * one, ordering them, and turning one off without losing it. Links a module
 * declared in code are listed alongside the site's own but cannot be edited
 * here, since they live in the module.
 */
@Controller
public class MenuController {

    public static final String PATH = "/admin/structure/menu";

    public static final String LABEL = "label";

    public static final String DESCRIPTION = "description";

    public static final String TITLE = "title";

    public static final String URL = "url";

    public static final String PARENT = "parent";

    public static final String WEIGHT = "weight";

    public static final String ENABLED = "enabled";

    public static final String EXPANDED = "expanded";

    /** The prefix each reordering input carries, one per link in the menu. */
    public static final String WEIGHT_PREFIX = "weight_";

    public static final String ENABLED_PREFIX = "enabled_";

    private final MenuManager menus;
    private final MenuTreeBuilder trees;
    private final MenuLinkContentService storedLinks;
    private final MachineNameGenerator machineNames;
    private final FormRenderer renderer;

    public MenuController(
            MenuManager menus,
            MenuTreeBuilder trees,
            MenuLinkContentService storedLinks,
            MachineNameGenerator machineNames,
            FormRenderer renderer) {
        this.menus = menus;
        this.trees = trees;
        this.storedLinks = storedLinks;
        this.machineNames = machineNames;
        this.renderer = renderer;
    }

    @GetMapping(PATH)
    public String list(Model model) {
        model.addAttribute("title", "Menus");
        model.addAttribute("description", "The menus this site navigates by.");
        model.addAttribute("action", PATH + "/add");
        model.addAttribute("formMarkup", renderer.render(menuForm()));
        model.addAttribute("menus", menus.all());
        model.addAttribute("managePath", PATH + "/manage/");
        return "admin/menu-list";
    }

    @PostMapping(PATH + "/add")
    public String add(
            @RequestParam(name = LABEL, defaultValue = "") String label,
            @RequestParam(name = DESCRIPTION, defaultValue = "") String description) {

        String id = machineNames.generateUnique(label, taken -> menus.find(taken).isPresent());
        menus.save(MenuConfig.of(id, label, description));
        return "redirect:" + PATH + "/manage/" + id;
    }

    @GetMapping(PATH + "/manage/{menu}/delete")
    public String confirmMenuDelete(@PathVariable String menu, Model model) {
        MenuConfig config = menus.find(menu).orElseThrow();

        model.addAttribute("title", "Delete the menu " + config.label() + "?");
        model.addAttribute("description", config.locked()
                ? "This menu cannot be deleted: the site itself hangs links in it."
                : "The links in this menu are deleted with it. This cannot be undone.");
        model.addAttribute("action", PATH + "/manage/" + menu + "/delete");
        model.addAttribute("formMarkup", renderer.render(
                confirmForm("Delete menu", PATH + "/manage/" + menu, !config.locked())));
        model.addAttribute("menus", List.of());
        model.addAttribute("managePath", PATH + "/manage/");
        return "admin/menu-list";
    }

    @PostMapping(PATH + "/manage/{menu}/delete")
    public String deleteMenu(@PathVariable String menu) {
        return menus.delete(menu) ? "redirect:" + PATH : "redirect:" + PATH + "/manage/" + menu;
    }

    @GetMapping(PATH + "/manage/{menu}")
    public String links(@PathVariable String menu, Model model) {
        MenuConfig config = menus.find(menu).orElseThrow();

        model.addAttribute("title", config.label());
        model.addAttribute("description", config.description());
        model.addAttribute("action", PATH + "/manage/" + menu);
        model.addAttribute("addPath", PATH + "/manage/" + menu + "/link/add");
        model.addAttribute("linkPath", PATH + "/link/");
        model.addAttribute("rows", rows(menu));
        model.addAttribute("weightPrefix", WEIGHT_PREFIX);
        model.addAttribute("enabledPrefix", ENABLED_PREFIX);
        return "admin/menu-links";
    }

    /**
     * Saves the order and the on/off state of every stored link in one go, which
     * is what the reordering table submits.
     */
    @PostMapping(PATH + "/manage/{menu}")
    public String reorder(@PathVariable String menu, @RequestParam Map<String, String> submitted) {
        for (MenuLinkRow row : rows(menu)) {
            if (!row.stored()) {
                continue;
            }
            MenuLinkContentService.entityId(row.id()).flatMap(storedLinks::find).ifPresent(link -> {
                String weight = submitted.get(WEIGHT_PREFIX + row.id());
                MenuLink reordered = link
                        .withWeight((weight == null || weight.isBlank()) ? 0 : Integer.parseInt(weight));
                storedLinks.save(submitted.containsKey(ENABLED_PREFIX + row.id())
                        ? reordered
                        : reordered.disabled());
            });
        }
        return "redirect:" + PATH + "/manage/" + menu;
    }

    @GetMapping(PATH + "/manage/{menu}/link/add")
    public String addLinkForm(@PathVariable String menu, Model model) {
        MenuLink blank = new MenuLink(null, menu, "", "", "", null, 0, false, true, null);

        model.addAttribute("title", "Add a link");
        model.addAttribute("description", "Where the link goes and where it hangs in the menu.");
        model.addAttribute("action", PATH + "/manage/" + menu + "/link/add");
        model.addAttribute("formMarkup", renderer.render(linkForm(blank)));
        return "admin/menu-form";
    }

    @PostMapping(PATH + "/manage/{menu}/link/add")
    public String addLink(@PathVariable String menu, @RequestParam Map<String, String> submitted) {
        storedLinks.save(submittedLink(null, menu, submitted));
        return "redirect:" + PATH + "/manage/" + menu;
    }

    @GetMapping(PATH + "/link/{id}/edit")
    public String editLinkForm(@PathVariable long id, Model model) {
        MenuLink link = storedLinks.find(id).orElseThrow();

        model.addAttribute("title", "Edit " + link.title());
        model.addAttribute("description", "Where the link goes and where it hangs in the menu.");
        model.addAttribute("action", PATH + "/link/" + id + "/edit");
        model.addAttribute("formMarkup", renderer.render(linkForm(link)));
        return "admin/menu-form";
    }

    @PostMapping(PATH + "/link/{id}/edit")
    public String editLink(@PathVariable long id, @RequestParam Map<String, String> submitted) {
        MenuLink link = storedLinks.find(id).orElseThrow();
        storedLinks.save(submittedLink(link.id(), link.menu(), submitted));
        return "redirect:" + PATH + "/manage/" + link.menu();
    }

    @GetMapping(PATH + "/link/{id}/delete")
    public String confirmLinkDelete(@PathVariable long id, Model model) {
        MenuLink link = storedLinks.find(id).orElseThrow();

        model.addAttribute("title", "Delete the link " + link.title() + "?");
        model.addAttribute("description", "This action cannot be undone.");
        model.addAttribute("action", PATH + "/link/" + id + "/delete");
        model.addAttribute("formMarkup", renderer.render(
                confirmForm("Delete link", PATH + "/manage/" + link.menu(), true)));
        return "admin/menu-form";
    }

    @PostMapping(PATH + "/link/{id}/delete")
    public String deleteLink(@PathVariable long id) {
        MenuLink link = storedLinks.find(id).orElseThrow();
        storedLinks.delete(id);
        return "redirect:" + PATH + "/manage/" + link.menu();
    }

    private List<MenuLinkRow> rows(String menu) {
        List<MenuLinkRow> rows = new ArrayList<>();
        collect(trees.buildForAdministration(menu).items(), 0, rows);
        return rows;
    }

    private static void collect(List<MenuTreeItem> items, int depth, List<MenuLinkRow> into) {
        for (MenuTreeItem item : items) {
            MenuLink link = item.link();
            into.add(new MenuLinkRow(link.id(), link.title(), link.url(), depth, link.weight(),
                    link.enabled(), MenuLinkContentService.entityId(link.id()).isPresent()));
            collect(item.children(), depth + 1, into);
        }
    }

    private MenuLink submittedLink(String id, String menu, Map<String, String> submitted) {
        String parent = submitted.getOrDefault(PARENT, "");
        String weight = submitted.getOrDefault(WEIGHT, "");
        return new MenuLink(
                id,
                menu,
                submitted.getOrDefault(TITLE, ""),
                submitted.getOrDefault(DESCRIPTION, ""),
                submitted.getOrDefault(URL, ""),
                parent.isBlank() ? null : parent,
                weight.isBlank() ? 0 : Integer.parseInt(weight),
                submitted.containsKey(EXPANDED),
                submitted.containsKey(ENABLED),
                null);
    }

    private FormElement menuForm() {
        return FormElement.of(ElementType.CONTAINER, "menu")
                .child(FormElement.of(ElementType.TEXTFIELD, LABEL)
                        .label("Menu name")
                        .markRequired()
                        .rule(ValidationRule.maxLength(64)))
                .child(FormElement.of(ElementType.TEXTFIELD, DESCRIPTION)
                        .label("Description")
                        .rule(ValidationRule.maxLength(255)))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "add").label("Add menu")));
    }

    private FormElement linkForm(MenuLink link) {
        return FormElement.of(ElementType.CONTAINER, "link")
                .child(FormElement.of(ElementType.TEXTFIELD, TITLE)
                        .label("Link title")
                        .markRequired()
                        .value(link.title())
                        .rule(ValidationRule.maxLength(255)))
                .child(FormElement.of(ElementType.TEXTFIELD, URL)
                        .label("Link")
                        .description("A path on this site, such as /admin/people.")
                        .markRequired()
                        .value(link.url())
                        .rule(ValidationRule.maxLength(2048)))
                .child(FormElement.of(ElementType.TEXTFIELD, DESCRIPTION)
                        .label("Description")
                        .value(link.description())
                        .rule(ValidationRule.maxLength(255)))
                .child(FormElement.of(ElementType.SELECT, PARENT)
                        .label("Parent link")
                        .value(link.parent() == null ? "" : link.parent())
                        .options(parentOptions(link)))
                .child(FormElement.of(ElementType.NUMBER, WEIGHT)
                        .label("Weight")
                        .description("Lighter links come first.")
                        .value(link.weight()))
                .child(FormElement.of(ElementType.CHECKBOX, EXPANDED)
                        .label("Show the links under this one")
                        .value(link.expanded()))
                .child(FormElement.of(ElementType.CHECKBOX, ENABLED)
                        .label("Enabled")
                        .value(link.enabled()))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save link"))
                        .child(FormElement.of(ElementType.LINK, "cancel")
                                .label("Cancel")
                                .value(PATH + "/manage/" + link.menu())));
    }

    /** Every other link in the menu, so a link can be hung under one of them. */
    private List<SelectOption> parentOptions(MenuLink link) {
        List<SelectOption> options = new ArrayList<>();
        options.add(new SelectOption("", "Top of the menu"));
        for (MenuLinkRow row : rows(link.menu())) {
            if (!row.id().equals(link.id())) {
                options.add(new SelectOption(row.id(), row.indent() + row.title()));
            }
        }
        return options;
    }

    private static FormElement confirmForm(String label, String cancelPath, boolean allowed) {
        FormElement actions = FormElement.of(ElementType.ACTIONS, "actions");
        if (allowed) {
            actions.child(FormElement.of(ElementType.SUBMIT, "confirm").label(label));
        }
        return FormElement.of(ElementType.CONTAINER, "confirm")
                .child(actions.child(FormElement.of(ElementType.LINK, "cancel")
                        .label("Cancel")
                        .value(cancelPath)));
    }
}
