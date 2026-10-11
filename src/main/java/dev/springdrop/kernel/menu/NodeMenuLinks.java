package dev.springdrop.kernel.menu;

import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.event.EntityEvent;
import dev.springdrop.kernel.node.NodeEntityType;
import java.util.Optional;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The menu link a node's form gives it: one stored link pointing at the node's
 * page. Deleting the node deletes its links.
 */
@Component
public class NodeMenuLinks {

    private final MenuLinkContentService links;

    public NodeMenuLinks(MenuLinkContentService links) {
        this.links = links;
    }

    /** The node's link, the first stored one pointing at its page. */
    public Optional<MenuLink> linkOf(Object nodeId) {
        return links.linkingTo(NodeEntityType.path(nodeId)).stream().findFirst();
    }

    /** Gives the node a link in the menu with the title and weight, or changes the one it has. */
    public MenuLink save(Object nodeId, String menu, String title, int weight) {
        String id = linkOf(nodeId).map(MenuLink::id).orElse(null);
        return links.save(MenuLink.of(id, menu, title, NodeEntityType.path(nodeId)).withWeight(weight));
    }

    /** Removes every stored link pointing at the node's page. */
    public void remove(Object nodeId) {
        links.linkingTo(NodeEntityType.path(nodeId)).forEach(link ->
                MenuLinkContentService.entityId(link.id()).ifPresent(links::delete));
    }

    @EventListener
    public void onNodeDeleted(EntityEvent event) {
        if (event.phase() == EntityEvent.Phase.DELETE && event.entityType().equals(NodeEntityType.ID)) {
            remove(((EntityData) event.entity()).id());
        }
    }
}
