package dev.springdrop.kernel.node;

import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.EntityQuery;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** The first nodes a query finds, leaving out any the person may not read, as a feed lists them. */
@Component
public class NodeListing {

    private final NodeService nodes;
    private final EntityAccessManager entityAccess;

    public NodeListing(NodeService nodes, EntityAccessManager entityAccess) {
        this.nodes = nodes;
        this.entityAccess = entityAccess;
    }

    /** The first nodes a query finds, as many as asked for, leaving out any the person may not read. */
    public List<EntityData> first(EntityQuery query, int count) {
        return readable(query.range(0, count).ids());
    }

    private List<EntityData> readable(List<Object> ids) {
        List<EntityData> found = new ArrayList<>();
        for (Object id : ids) {
            nodes.find(((Number) id).longValue())
                    .filter(node -> entityAccess.may(NodeEntityType.ID, node, EntityAccessHandler.VIEW))
                    .ifPresent(found::add);
        }
        return found;
    }
}
