package dev.springdrop.kernel.rest;

import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeService;
import org.springframework.stereotype.Component;

/**
 * Saves and deletes entities written over REST. A node goes through the node
 * service, which stamps when it was created and changed, keeps a revision by
 * the account, and removes its aliases on delete. Any other entity is written
 * as it is.
 */
@Component
public class RestEntityWriter {

    private final EntityCrudService entities;
    private final NodeService nodes;

    public RestEntityWriter(EntityCrudService entities, NodeService nodes) {
        this.entities = entities;
        this.nodes = nodes;
    }

    public EntityData save(EntityData entity, long accountId) {
        return entity.entityType().equals(NodeEntityType.ID) ? nodes.save(entity, accountId) : entities.save(entity);
    }

    public void delete(String entityType, long id) {
        if (entityType.equals(NodeEntityType.ID)) {
            nodes.delete(id);
        } else {
            entities.delete(entityType, id);
        }
    }
}
