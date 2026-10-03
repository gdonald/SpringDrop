package dev.springdrop.web;

import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.layout.LayoutDisplayManager;
import dev.springdrop.kernel.layout.Section;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeLayoutAccess;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.web.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * One node's own layout, where its content type's full view mode allows one.
 * The editor starts from the content type's layout, and the first change saves
 * a copy on the node, which every later change edits. Each change saves the
 * node, so it keeps a revision, and reverting puts the node back on its type's
 * layout.
 *
 * <p>{@link NodeLayoutAccess} decides who may change a node's layout.
 */
@Controller
public class NodeLayoutController {

    static final String BASE = "/node/{id}/layout";

    private final NodeService nodes;
    private final LayoutDisplayManager displays;
    private final LayoutEditor editor;
    private final NodeLayoutAccess layoutAccess;

    public NodeLayoutController(
            NodeService nodes, LayoutDisplayManager displays, LayoutEditor editor, NodeLayoutAccess layoutAccess) {
        this.nodes = nodes;
        this.displays = displays;
        this.editor = editor;
        this.layoutAccess = layoutAccess;
    }

    public static String layoutPath(Object id) {
        return NodeEntityType.path(id) + "/layout";
    }

    @GetMapping(BASE)
    public String editorPage(@PathVariable long id, Model model) {
        EntityData node = editableNode(id);
        model.addAttribute("title", "Layout of " + node.label());
        model.addAttribute("backPath", NodeEntityType.path(id));
        model.addAttribute("backLabel", "Back to the content");
        model.addAttribute("defaults", false);
        model.addAttribute("enabled", true);
        model.addAttribute("hasOverride", displays.overrideOf(node).isPresent());
        editor.describe(target(node), model);
        return "admin/layout-builder";
    }

    @PostMapping(BASE)
    public String save(@PathVariable long id, @RequestParam Map<String, String> submitted) {
        LayoutTarget target = target(editableNode(id));
        target.save(editor.arranged(target.sections(), submitted));
        return "redirect:" + target.path();
    }

    /** Puts the node back on its content type's layout. */
    @PostMapping(BASE + "/revert")
    public String revert(@PathVariable long id) {
        EntityData node = editableNode(id);
        nodes.save(displays.withoutOverride(node), CurrentAccount.id());
        return "redirect:" + layoutPath(id);
    }

    @PostMapping(BASE + "/section/add")
    public String addSection(@PathVariable long id,
            @RequestParam(name = LayoutEditor.LAYOUT, defaultValue = "") String layout) {
        return editor.addSection(target(editableNode(id)), layout);
    }

    @PostMapping(BASE + "/section/{section}/remove")
    public String removeSection(@PathVariable long id, @PathVariable int section) {
        return editor.removeSection(target(editableNode(id)), section);
    }

    @GetMapping(BASE + "/section/{section}/region/{region}/library")
    public String library(@PathVariable long id, @PathVariable int section, @PathVariable String region,
            Model model) {
        return editor.library(target(editableNode(id)), section, region, model);
    }

    @GetMapping(BASE + "/section/{section}/region/{region}/add/{plugin}")
    public String addBlockForm(@PathVariable long id, @PathVariable int section, @PathVariable String region,
            @PathVariable String plugin, Model model) {
        return editor.addBlockForm(target(editableNode(id)), section, region, plugin, model);
    }

    @PostMapping(BASE + "/section/{section}/region/{region}/add/{plugin}")
    public String addBlock(@PathVariable long id, @PathVariable int section, @PathVariable String region,
            @PathVariable String plugin, @RequestParam Map<String, String> submitted, Model model) {
        return editor.addBlock(target(editableNode(id)), section, region, plugin, submitted, model);
    }

    @GetMapping(BASE + "/block/{block}")
    public String configureForm(@PathVariable long id, @PathVariable String block, Model model) {
        return editor.configureForm(target(editableNode(id)), block, model);
    }

    @PostMapping(BASE + "/block/{block}")
    public String configure(@PathVariable long id, @PathVariable String block,
            @RequestParam Map<String, String> submitted, Model model) {
        return editor.configure(target(editableNode(id)), block, submitted, model);
    }

    @PostMapping(BASE + "/block/{block}/remove")
    public String removeBlock(@PathVariable long id, @PathVariable String block) {
        return editor.removeBlock(target(editableNode(id)), block);
    }

    private LayoutTarget target(EntityData node) {
        List<Section> sections = displays.overrideOf(node)
                .orElseGet(() -> layoutAccess.overridableDisplay(node).orElseThrow().sections());
        return new LayoutTarget(NodeEntityType.ID, node.bundle(), layoutPath(node.id()), sections,
                changed -> nodes.save(displays.withOverride(node, changed), CurrentAccount.id()));
    }

    private EntityData editableNode(long id) {
        EntityData node = nodes.find(id).orElseThrow(() -> new EntityNotFoundException("node", String.valueOf(id)));
        if (layoutAccess.overridableDisplay(node).isEmpty()) {
            throw new EntityNotFoundException("layout", "node " + id);
        }
        if (!layoutAccess.mayOverride(node)) {
            throw new AccessDeniedException("You may not change the layout of this content.");
        }
        return node;
    }
}
