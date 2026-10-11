package dev.springdrop.kernel.action;

import dev.springdrop.kernel.batch.BatchDefinition;
import dev.springdrop.kernel.batch.BatchManager;
import dev.springdrop.kernel.batch.BatchOperationSpec;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.views.HandlerConfig;
import dev.springdrop.kernel.views.ViewConfig;
import dev.springdrop.kernel.views.handlers.BulkFormField;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Applying an action to the results ticked in a listing. The listing offers
 * the actions its bulk form field names, and the chosen one runs over the
 * selection as a batch, an operation per entity.
 */
@Component
public class BulkOperations {

    private final ActionManager actions;
    private final BatchManager batches;
    private final EntityCrudService entities;

    public BulkOperations(ActionManager actions, BatchManager batches, EntityCrudService entities) {
        this.actions = actions;
        this.batches = batches;
        this.entities = entities;
    }

    /** One ticked result: its entity type and id, sent as {@code type:id}. */
    public record Item(String entityType, long id) {

        public static Optional<Item> parse(String value) {
            String[] parts = value.split(":", -1);
            if (parts.length != 2 || parts[0].isEmpty() || !parts[1].matches("\\d{1,18}")) {
                return Optional.empty();
            }
            return Optional.of(new Item(parts[0], Long.parseLong(parts[1])));
        }
    }

    /** The display's bulk form field, when it has one. */
    public Optional<HandlerConfig> bulkField(ViewConfig view, String displayId) {
        List<HandlerConfig> fields = view.options(displayId).fields();
        return Stream.ofNullable(fields).flatMap(List::stream)
                .filter(field -> field.plugin().equals(BulkFormField.ID))
                .findFirst();
    }

    /** The actions the display's bulk form offers for its listed type, by label. None without a bulk form. */
    public List<ActionConfig> offered(ViewConfig view, String displayId) {
        return bulkField(view, displayId).map(field -> {
            List<String> named = Arrays.stream(field.text(BulkFormField.ACTIONS).split(","))
                    .map(String::strip).filter(name -> !name.isEmpty()).toList();
            return actions.actionsFor(view.baseEntityType()).stream()
                    .filter(action -> named.isEmpty() || named.contains(action.id()))
                    .toList();
        }).orElse(List.of());
    }

    public boolean destructive(ActionConfig action) {
        return actions.plugin(action).destructive();
    }

    /** The entities ticked that still exist, in the order ticked. */
    public List<EntityData> load(List<Item> items) {
        return items.stream()
                .flatMap(item -> entities.load(item.entityType(), item.id()).stream())
                .toList();
    }

    /** Starts the batch applying the action to each item, finishing at the destination, and gives its id. */
    public String start(ActionConfig action, List<Item> items, String destination) {
        String batchId = UUID.randomUUID().toString();
        batches.start(BatchDefinition.of(batchId, items.stream()
                        .map(item -> BatchOperationSpec.of(ActionBatchHandler.ID, Map.of(
                                ActionBatchHandler.ACTION, action.id(),
                                ActionBatchHandler.ENTITY_TYPE, item.entityType(),
                                ActionBatchHandler.ENTITY_ID, String.valueOf(item.id()))))
                        .toList())
                .finishingAt(destination));
        return batchId;
    }
}
