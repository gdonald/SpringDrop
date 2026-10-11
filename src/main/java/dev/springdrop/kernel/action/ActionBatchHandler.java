package dev.springdrop.kernel.action;

import dev.springdrop.kernel.batch.BatchOperationHandler;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import java.util.Map;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Applies an action to one entity as a batch operation, as the person whose
 * request runs the batch. An entity deleted since it was chosen, or one the
 * person may not apply the action to, fails that one operation.
 */
@Component
public class ActionBatchHandler implements BatchOperationHandler {

    public static final String ID = "action";

    public static final String ACTION = "action";

    public static final String ENTITY_TYPE = "entity_type";

    public static final String ENTITY_ID = "id";

    private final ActionManager actions;
    private final EntityCrudService entities;

    public ActionBatchHandler(ActionManager actions, EntityCrudService entities) {
        this.actions = actions;
        this.entities = entities;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void run(Map<String, Object> payload) {
        String entityType = String.valueOf(payload.get(ENTITY_TYPE));
        long id = Long.parseLong(String.valueOf(payload.get(ENTITY_ID)));
        EntityData entity = entities.load(entityType, id).orElseThrow(
                () -> new IllegalStateException("The " + entityType + " " + id + " no longer exists."));
        actions.execute(String.valueOf(payload.get(ACTION)), entity,
                SecurityContextHolder.getContext().getAuthentication());
    }
}
