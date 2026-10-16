package dev.springdrop.web;

import dev.springdrop.kernel.cache.CacheContexts;
import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.render.PlaceholderBuilder;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.theme.Contextual;
import dev.springdrop.kernel.user.AccountPrincipals;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Draws a block's or a node's contextual links for the person viewing it:
 * "Configure block" for someone who administers blocks, and "Edit" and
 * "Delete" on a node for someone who may change or remove it. Someone who may
 * follow none sees nothing.
 */
@Component
public class ContextualLinksBuilder implements PlaceholderBuilder {

    private final NodeService nodes;
    private final EntityAccessManager access;

    public ContextualLinksBuilder(NodeService nodes, EntityAccessManager access) {
        this.nodes = nodes;
        this.access = access;
    }

    /** One contextual link: what it says and where it leads. */
    private record Link(String label, String href) {
    }

    @Override
    public String id() {
        return Contextual.PLACEHOLDER;
    }

    @Override
    public Renderable build(Map<String, String> arguments) {
        Authentication who = SecurityContextHolder.getContext().getAuthentication();
        String id = arguments.get(Contextual.ID);
        List<Link> links = new ArrayList<>();
        if (Contextual.BLOCK.equals(arguments.get(Contextual.KIND))) {
            if (AccountPrincipals.bypassesChecks(who) || holds(who, Permissions.ADMINISTER_BLOCKS)) {
                links.add(new Link("Configure block", BlockLayoutController.PATH + "/manage/" + id));
            }
        } else {
            nodes.find(Long.parseLong(id)).ifPresent(node -> {
                if (access.check(NodeEntityType.ID, node, EntityAccessHandler.UPDATE, who).allowed()) {
                    links.add(new Link("Edit", NodeEntityType.editPath(id)));
                }
                if (access.check(NodeEntityType.ID, node, EntityAccessHandler.DELETE, who).allowed()) {
                    links.add(new Link("Delete", NodeController.deletePath(id)));
                }
            });
        }
        StringBuilder markup = new StringBuilder();
        if (!links.isEmpty()) {
            markup.append("<div class=\"contextual-links d-flex justify-content-end gap-1 mb-1\" data-contextual=\"")
                    .append(HtmlUtils.htmlEscape(arguments.get(Contextual.KIND) + ":" + id)).append("\">");
            links.forEach(link -> markup.append("<a class=\"btn btn-secondary btn-sm\" href=\"")
                    .append(HtmlUtils.htmlEscape(link.href())).append("\">").append(HtmlUtils.htmlEscape(link.label()))
                    .append("</a>"));
            markup.append("</div>");
        }
        return Renderable.of("markup").with("value", markup.toString()).cacheContext(CacheContexts.USER);
    }

    private static boolean holds(Authentication who, String permission) {
        return who != null && who.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals(permission));
    }
}
