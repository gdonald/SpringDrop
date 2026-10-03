package dev.springdrop.kernel.node;

import dev.springdrop.kernel.config.ConfigStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** The content types the site has, each stored as the config entity {@code node_type.<id>}. */
@Component
public class NodeTypeManager {

    private final ConfigStore configStore;

    public NodeTypeManager(ConfigStore configStore) {
        this.configStore = configStore;
    }

    public static String configName(String id) {
        return NodeEntityType.TYPE_ID + "." + id;
    }

    public void save(NodeType type) {
        configStore.save(configName(type.id()), type);
    }

    public Optional<NodeType> find(String id) {
        return Optional.ofNullable(configStore.read(configName(id), NodeType.class, null));
    }

    public void delete(String id) {
        configStore.delete(configName(id));
    }

    /** Every content type, listed by label. */
    public List<NodeType> all() {
        List<NodeType> types = new ArrayList<>();
        for (String name : configStore.listNames(NodeEntityType.TYPE_ID)) {
            find(name.substring(NodeEntityType.TYPE_ID.length() + 1)).ifPresent(types::add);
        }
        return types.stream()
                .sorted(Comparator.comparing(NodeType::label).thenComparing(NodeType::id))
                .toList();
    }
}
