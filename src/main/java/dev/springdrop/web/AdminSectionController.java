package dev.springdrop.web;

import dev.springdrop.kernel.menu.AdminToolbar;
import dev.springdrop.kernel.menu.MenuConfig;
import dev.springdrop.kernel.menu.MenuTreeBuilder;
import dev.springdrop.kernel.menu.MenuTreeItem;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The administration overview pages: one listing every section of the
 * Administration menu, and one per section listing the pages under it, each
 * leaving out what the person may not open.
 */
@Controller
public class AdminSectionController {

    public static final String PATH = "/admin";

    public static final String STRUCTURE_PATH = "/admin/structure";

    public static final String CONFIGURATION_PATH = "/admin/config";

    public static final String REPORTS_PATH = "/admin/reports";

    private final MenuTreeBuilder trees;

    public AdminSectionController(MenuTreeBuilder trees) {
        this.trees = trees;
    }

    @GetMapping(PATH)
    public String index(Model model) {
        return page("Administration", AdminToolbar.reachableSections(trees.build(MenuConfig.ADMIN, PATH).items()),
                model);
    }

    @GetMapping(STRUCTURE_PATH)
    public String structure(Model model) {
        return section(CoreMenuLinks.STRUCTURE, "Structure", model);
    }

    @GetMapping(CONFIGURATION_PATH)
    public String configuration(Model model) {
        return section(CoreMenuLinks.CONFIGURATION, "Configuration", model);
    }

    @GetMapping(REPORTS_PATH)
    public String reports(Model model) {
        return section(CoreMenuLinks.REPORTS, "Reports", model);
    }

    private String section(String sectionId, String title, Model model) {
        List<MenuTreeItem> pages = trees.build(MenuConfig.ADMIN).items().stream()
                .filter(item -> item.link().id().equals(sectionId)).findFirst()
                .map(MenuTreeItem::children).orElse(List.of());
        return page(title, pages, model);
    }

    private static String page(String title, List<MenuTreeItem> items, Model model) {
        model.addAttribute("title", title);
        model.addAttribute("items", items);
        return "admin/section";
    }
}
