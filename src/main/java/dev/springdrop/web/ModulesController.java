package dev.springdrop.web;

import dev.springdrop.kernel.module.ModuleDependencyException;
import dev.springdrop.kernel.module.ModuleInfo;
import dev.springdrop.kernel.module.ModuleManager;
import dev.springdrop.kernel.module.ModuleRegistry;
import dev.springdrop.kernel.theme.Messenger;
import dev.springdrop.kernel.theme.StatusMessage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The modules page: every module by package, ticked when it is enabled.
 * Ticking a module enables it and unticking one disables it. Enabling a module
 * whose dependencies are off asks first, naming the modules that come with it.
 * A module an enabled module depends on cannot be unticked, so its box is
 * disabled and a submission unticking it is refused.
 */
@Controller
public class ModulesController {

    public static final String PATH = "/admin/modules";

    public static final String CONFIRM_PATH = PATH + "/confirm";

    public static final String ENABLE_PREFIX = "enable_";

    public static final String MODULES = "modules";

    private final ModuleRegistry registry;
    private final ModuleManager modules;
    private final Messenger messenger;

    public ModulesController(ModuleRegistry registry, ModuleManager modules, Messenger messenger) {
        this.registry = registry;
        this.modules = modules;
        this.messenger = messenger;
    }

    /** One module's row: what it is, whether it is on, and how it relates to the others. */
    public record Row(String name, String label, String description, String version, boolean enabled,
            boolean locked, List<String> requires, List<String> requiredBy) {
    }

    @GetMapping(PATH)
    public String list(Model model) {
        Map<String, List<Row>> packages = new TreeMap<>();
        registry.all().stream().sorted(Comparator.comparing(ModuleInfo::label)).forEach(module -> packages
                .computeIfAbsent(module.packageName(), name -> new ArrayList<>()).add(row(module)));
        model.addAttribute("title", "Extend");
        model.addAttribute("packages", packages);
        model.addAttribute("action", PATH);
        return "admin/modules";
    }

    @PostMapping(PATH)
    public String save(@RequestParam Map<String, String> submitted, Model model) {
        List<String> enabling = new ArrayList<>();
        List<String> disabling = new ArrayList<>();
        for (ModuleInfo module : registry.installOrder()) {
            boolean ticked = submitted.containsKey(ENABLE_PREFIX + module.name());
            if (ticked && !modules.isEnabled(module.name())) {
                enabling.add(module.name());
            } else if (!ticked && modules.isEnabled(module.name())) {
                disabling.add(module.name());
            }
        }
        Set<String> alsoNeeded = new LinkedHashSet<>();
        enabling.forEach(name -> collectMissing(name, enabling, alsoNeeded));
        if (!alsoNeeded.isEmpty()) {
            model.addAttribute("title", "Some required modules must be enabled");
            model.addAttribute("enabling", labels(enabling));
            model.addAttribute("alsoNeeded", labels(new ArrayList<>(alsoNeeded)));
            model.addAttribute("modules", String.join(",", enabling));
            model.addAttribute("disabling", String.join(",", disabling));
            model.addAttribute("action", CONFIRM_PATH);
            model.addAttribute("cancelPath", PATH);
            return "admin/modules-confirm";
        }
        return apply(enabling, disabling);
    }

    @PostMapping(CONFIRM_PATH)
    public String confirm(@RequestParam(name = MODULES, defaultValue = "") String enabling,
            @RequestParam(name = "disabling", defaultValue = "") String disabling) {
        return apply(split(enabling), split(disabling));
    }

    private String apply(List<String> enabling, List<String> disabling) {
        enabling.forEach(modules::enable);
        List<String> refused = new ArrayList<>();
        for (String name : disabling.reversed()) {
            try {
                modules.disable(name);
            } catch (ModuleDependencyException needed) {
                refused.add(needed.getMessage());
            }
        }
        if (refused.isEmpty()) {
            messenger.success("The configuration options have been saved.");
        } else {
            refused.forEach(reason -> messenger.add(StatusMessage.error(reason)));
        }
        return "redirect:" + PATH;
    }

    private Row row(ModuleInfo module) {
        List<String> requiredBy = registry.all().stream().filter(other -> other.dependencies().contains(module.name()))
                .map(ModuleInfo::label).sorted().toList();
        boolean locked = registry.all().stream().anyMatch(other -> other.dependencies().contains(module.name())
                && modules.isEnabled(other.name()));
        return new Row(module.name(), module.label(), module.description(), module.version(),
                modules.isEnabled(module.name()), locked, labels(module.dependencies()), requiredBy);
    }

    /** Adds the dependencies of a module being enabled that are off and not being enabled with it. */
    private void collectMissing(String name, List<String> enabling, Set<String> missing) {
        for (String dependency : registry.find(name).orElseThrow().dependencies()) {
            if (!modules.isEnabled(dependency) && !enabling.contains(dependency) && missing.add(dependency)) {
                collectMissing(dependency, enabling, missing);
            }
        }
    }

    private List<String> labels(List<String> names) {
        return names.stream().map(name -> registry.find(name).map(ModuleInfo::label).orElse(name)).toList();
    }

    private static List<String> split(String names) {
        return names.isBlank() ? List.of() : List.of(names.split(","));
    }
}
