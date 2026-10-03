package dev.springdrop.web;

import dev.springdrop.kernel.entity.BundleManager;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.layout.LayoutBuilderDisplay;
import dev.springdrop.kernel.layout.LayoutDisplayManager;
import dev.springdrop.kernel.web.EntityNotFoundException;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Layout Builder for one bundle's view mode: turning it on and off, deciding
 * whether each entity may have a layout of its own, and editing the layout the
 * bundle's entities share.
 */
@Controller
public class LayoutBuilderController {

    static final String BASE = FieldUiController.PATH_PREFIX + "/{entityType}/{bundle}/display/{mode}/layout";

    public static final String ALLOW_OVERRIDES = "allow_overrides";

    private static final String MACHINE_NAME = "[a-z0-9_]+";

    private final LayoutDisplayManager displays;
    private final LayoutEditor editor;
    private final EntityTypeManager entityTypes;
    private final BundleManager bundles;

    public LayoutBuilderController(
            LayoutDisplayManager displays, LayoutEditor editor, EntityTypeManager entityTypes, BundleManager bundles) {
        this.displays = displays;
        this.editor = editor;
        this.entityTypes = entityTypes;
        this.bundles = bundles;
    }

    public static String layoutPath(String entityTypeId, String bundle, String mode) {
        return FieldUiController.PATH_PREFIX + "/" + entityTypeId + "/" + bundle + "/display/" + mode + "/layout";
    }

    @GetMapping(BASE)
    public String editorPage(@PathVariable String entityType, @PathVariable String bundle, @PathVariable String mode,
            Model model) {
        EntityType type = requireBundle(entityType, bundle, mode);
        String path = layoutPath(entityType, bundle, mode);

        model.addAttribute("title", "Layout of " + bundle + " in the " + mode + " view mode");
        model.addAttribute("action", path);
        model.addAttribute("backPath", DisplayUiController.viewDisplayPath(entityType, bundle)
                + "?" + DisplayUiController.MODE + "=" + mode);
        model.addAttribute("backLabel", "Back to Manage display");
        model.addAttribute("defaults", true);
        model.addAttribute("overridable", overridable(type));
        LayoutBuilderDisplay display = displays.find(entityType, bundle, mode)
                .filter(LayoutBuilderDisplay::enabled)
                .orElse(null);
        model.addAttribute("enabled", display != null);
        if (display != null) {
            model.addAttribute("allowOverrides", display.allowOverrides());
            editor.describe(target(display, path), model);
        }
        return "admin/layout-builder";
    }

    @PostMapping(BASE + "/enable")
    public String enable(@PathVariable String entityType, @PathVariable String bundle, @PathVariable String mode) {
        requireBundle(entityType, bundle, mode);
        displays.enable(entityType, bundle, mode);
        return "redirect:" + layoutPath(entityType, bundle, mode);
    }

    @PostMapping(BASE + "/disable")
    public String disable(@PathVariable String entityType, @PathVariable String bundle, @PathVariable String mode) {
        requireBundle(entityType, bundle, mode);
        displays.disable(entityType, bundle, mode);
        return "redirect:" + layoutPath(entityType, bundle, mode);
    }

    /**
     * Saves every section's widths, every block's region and weight, and
     * whether each entity may have a layout of its own, which only an entity
     * type with somewhere to store one may allow.
     */
    @PostMapping(BASE)
    public String save(@PathVariable String entityType, @PathVariable String bundle, @PathVariable String mode,
            @RequestParam Map<String, String> submitted) {
        EntityType type = requireBundle(entityType, bundle, mode);
        LayoutBuilderDisplay display = enabledDisplay(entityType, bundle, mode);
        displays.save(display.withSections(editor.arranged(display.sections(), submitted))
                .withOverridesAllowed(overridable(type) && submitted.containsKey(ALLOW_OVERRIDES)));
        return "redirect:" + layoutPath(entityType, bundle, mode);
    }

    @PostMapping(BASE + "/section/add")
    public String addSection(@PathVariable String entityType, @PathVariable String bundle, @PathVariable String mode,
            @RequestParam(name = LayoutEditor.LAYOUT, defaultValue = "") String layout) {
        return editor.addSection(target(entityType, bundle, mode), layout);
    }

    @PostMapping(BASE + "/section/{section}/remove")
    public String removeSection(@PathVariable String entityType, @PathVariable String bundle,
            @PathVariable String mode, @PathVariable int section) {
        return editor.removeSection(target(entityType, bundle, mode), section);
    }

    @GetMapping(BASE + "/section/{section}/region/{region}/library")
    public String library(@PathVariable String entityType, @PathVariable String bundle, @PathVariable String mode,
            @PathVariable int section, @PathVariable String region, Model model) {
        return editor.library(target(entityType, bundle, mode), section, region, model);
    }

    @GetMapping(BASE + "/section/{section}/region/{region}/add/{plugin}")
    public String addBlockForm(@PathVariable String entityType, @PathVariable String bundle,
            @PathVariable String mode, @PathVariable int section, @PathVariable String region,
            @PathVariable String plugin, Model model) {
        return editor.addBlockForm(target(entityType, bundle, mode), section, region, plugin, model);
    }

    @PostMapping(BASE + "/section/{section}/region/{region}/add/{plugin}")
    public String addBlock(@PathVariable String entityType, @PathVariable String bundle, @PathVariable String mode,
            @PathVariable int section, @PathVariable String region, @PathVariable String plugin,
            @RequestParam Map<String, String> submitted, Model model) {
        return editor.addBlock(target(entityType, bundle, mode), section, region, plugin, submitted, model);
    }

    @GetMapping(BASE + "/block/{block}")
    public String configureForm(@PathVariable String entityType, @PathVariable String bundle,
            @PathVariable String mode, @PathVariable String block, Model model) {
        return editor.configureForm(target(entityType, bundle, mode), block, model);
    }

    @PostMapping(BASE + "/block/{block}")
    public String configure(@PathVariable String entityType, @PathVariable String bundle,
            @PathVariable String mode, @PathVariable String block, @RequestParam Map<String, String> submitted,
            Model model) {
        return editor.configure(target(entityType, bundle, mode), block, submitted, model);
    }

    @PostMapping(BASE + "/block/{block}/remove")
    public String removeBlock(@PathVariable String entityType, @PathVariable String bundle,
            @PathVariable String mode, @PathVariable String block) {
        return editor.removeBlock(target(entityType, bundle, mode), block);
    }

    /** Whether entities of the type have somewhere to keep a layout of their own. */
    static boolean overridable(EntityType type) {
        return type.baseFields().stream()
                .anyMatch(field -> field.name().equals(LayoutDisplayManager.OVERRIDE_FIELD));
    }

    private LayoutTarget target(String entityTypeId, String bundle, String mode) {
        return target(enabledDisplay(entityTypeId, bundle, mode), layoutPath(entityTypeId, bundle, mode));
    }

    private LayoutTarget target(LayoutBuilderDisplay display, String path) {
        return new LayoutTarget(display.entityTypeId(), display.bundle(), path, display.sections(),
                changed -> displays.save(display.withSections(changed)));
    }

    /** The bundle has to exist, and the view mode has to be a machine name. */
    private EntityType requireBundle(String entityTypeId, String bundle, String mode) {
        EntityType type = entityTypes.find(entityTypeId)
                .orElseThrow(() -> new EntityNotFoundException("entity type", entityTypeId));
        boolean bundleExists = (type.bundleEntityType() == null)
                ? bundle.equals(entityTypeId)
                : bundles.find(entityTypeId, bundle).isPresent();
        if (!bundleExists) {
            throw new EntityNotFoundException("bundle", bundle);
        }
        if (!mode.matches(MACHINE_NAME)) {
            throw new EntityNotFoundException("view mode", mode);
        }
        return type;
    }

    private LayoutBuilderDisplay enabledDisplay(String entityTypeId, String bundle, String mode) {
        requireBundle(entityTypeId, bundle, mode);
        return displays.find(entityTypeId, bundle, mode)
                .filter(LayoutBuilderDisplay::enabled)
                .orElseThrow(() -> new EntityNotFoundException("layout", bundle + " " + mode));
    }
}
