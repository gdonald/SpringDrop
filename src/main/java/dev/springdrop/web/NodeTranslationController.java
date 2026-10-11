package dev.springdrop.web;

import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.language.LanguageConfig;
import dev.springdrop.kernel.language.LanguageManager;
import dev.springdrop.kernel.log.ActivityLog;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.user.AccountPrincipals;
import dev.springdrop.kernel.web.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * A node's translations: an overview listing every language with the node's
 * title in it, adding a translation as a copy of the node to be edited, and
 * deleting one. It is open to someone who may change the node and holds
 * {@code translate content}, on a site with more than one language.
 */
@Controller
public class NodeTranslationController {

    private final NodeService nodes;
    private final EntityCrudService entities;
    private final EntityAccessManager access;
    private final LanguageManager languages;
    private final ActivityLog log;

    public NodeTranslationController(NodeService nodes, EntityCrudService entities, EntityAccessManager access,
            LanguageManager languages, ActivityLog log) {
        this.nodes = nodes;
        this.entities = entities;
        this.access = access;
        this.languages = languages;
        this.log = log;
    }

    public static String overviewPath(Object id) {
        return NodeEntityType.path(id) + "/translations";
    }

    public static String addPath(Object id, String langcode) {
        return overviewPath(id) + "/add/" + langcode;
    }

    public static String deletePath(Object id, String langcode) {
        return overviewPath(id) + "/delete/" + langcode;
    }

    /** Whether the person making the request may translate the node. */
    public static boolean mayTranslate(EntityData node, EntityAccessManager access, LanguageManager languages) {
        Authentication who = SecurityContextHolder.getContext().getAuthentication();
        boolean permitted = AccountPrincipals.bypassesChecks(who) || who.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(Permissions.TRANSLATE_CONTENT));
        return permitted && languages.all().size() > 1
                && access.may(NodeEntityType.ID, node, EntityAccessHandler.UPDATE);
    }

    /** One language of the overview: the node's title in it, if it has one, and how it stands. */
    public record Row(LanguageConfig language, String title, String status, boolean original, boolean translated) {
    }

    @GetMapping("/node/{id}/translations")
    public String overview(@PathVariable long id, Model model) {
        EntityData node = translatable(id);
        List<String> held = entities.translations(NodeEntityType.ID, node.id());
        List<Row> rows = languages.all().stream().map(language -> {
            boolean original = language.id().equals(held.getFirst());
            boolean translated = held.contains(language.id());
            String title = translated ? nodes.find(id, language.id()).orElseThrow().label() : "";
            return new Row(language, title, original ? "Original language" : translated ? "Translated"
                    : "Not translated", original, translated);
        }).toList();
        model.addAttribute("title", "Translations of " + node.label());
        model.addAttribute("rows", rows);
        model.addAttribute("nodeId", node.id());
        model.addAttribute("editPath", NodeEntityType.editPath(node.id()) + "?" + NodeController.LANGCODE + "=");
        model.addAttribute("addPath", overviewPath(node.id()) + "/add/");
        model.addAttribute("deletePath", overviewPath(node.id()) + "/delete/");
        return "node/translations";
    }

    /** Adds the translation as a copy of the node in its own language, and opens it for editing. */
    @PostMapping("/node/{id}/translations/add/{langcode}")
    public String add(@PathVariable long id, @PathVariable String langcode) {
        EntityData node = translatable(id);
        LanguageConfig language = languages.find(langcode)
                .filter(found -> !entities.translations(NodeEntityType.ID, node.id()).contains(found.id()))
                .orElseThrow(() -> new EntityNotFoundException("missing translation", langcode));
        nodes.save(node.withLangcode(language.id()), CurrentAccount.id());
        log.channel(NodeController.LOG_CHANNEL).notice("{type}: added the {language} translation of {title}.",
                Map.of("type", node.bundle(), "language", language.label(), "title", node.label()));
        return "redirect:" + NodeEntityType.editPath(node.id()) + "?" + NodeController.LANGCODE + "=" + langcode;
    }

    @GetMapping("/node/{id}/translations/delete/{langcode}")
    public String confirmDelete(@PathVariable long id, @PathVariable String langcode, Model model) {
        EntityData node = translatable(id);
        LanguageConfig language = translation(node, langcode);
        model.addAttribute("title", "Delete the " + language.label() + " translation of " + node.label() + "?");
        model.addAttribute("description", "This action cannot be undone.");
        model.addAttribute("action", deletePath(id, langcode));
        model.addAttribute("confirmLabel", "Delete translation");
        model.addAttribute("cancelPath", overviewPath(id));
        return "admin/confirm";
    }

    @PostMapping("/node/{id}/translations/delete/{langcode}")
    public String delete(@PathVariable long id, @PathVariable String langcode) {
        EntityData node = translatable(id);
        LanguageConfig language = translation(node, langcode);
        entities.deleteTranslation(NodeEntityType.ID, node.id(), language.id());
        log.channel(NodeController.LOG_CHANNEL).notice("{type}: deleted the {language} translation of {title}.",
                Map.of("type", node.bundle(), "language", language.label(), "title", node.label()));
        return "redirect:" + overviewPath(id);
    }

    /** The node in its own language, when the person may translate it. */
    private EntityData translatable(long id) {
        EntityData node = nodes.find(id).orElseThrow(() -> new EntityNotFoundException("node", String.valueOf(id)));
        EntityData own = nodes.find(id, entities.translations(NodeEntityType.ID, id).getFirst()).orElseThrow();
        if (!mayTranslate(own, access, languages)) {
            throw new AccessDeniedException("You may not translate " + node.label() + ".");
        }
        return own;
    }

    /** A language the node is translated into, apart from its own. */
    private LanguageConfig translation(EntityData node, String langcode) {
        List<String> held = entities.translations(NodeEntityType.ID, node.id());
        return languages.find(langcode).filter(language -> held.indexOf(language.id()) > 0)
                .orElseThrow(() -> new EntityNotFoundException("translation", langcode));
    }
}
