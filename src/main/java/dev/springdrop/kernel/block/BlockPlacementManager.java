package dev.springdrop.kernel.block;

import dev.springdrop.kernel.config.ConfigStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** The blocks placed in the site's themes, read and written through the config store. */
@Component
public class BlockPlacementManager {

    /** Region, then weight, then label: the order a region draws its blocks in. */
    public static final Comparator<BlockPlacement> DRAWING_ORDER = Comparator
            .comparing(BlockPlacement::region)
            .thenComparingInt(BlockPlacement::weight)
            .thenComparing(BlockPlacement::label);

    private final ConfigStore configStore;

    public BlockPlacementManager(ConfigStore configStore) {
        this.configStore = configStore;
    }

    public void save(BlockPlacement placement) {
        configStore.save(BlockPlacement.configName(placement.id()), placement);
    }

    public Optional<BlockPlacement> find(String id) {
        return Optional.ofNullable(configStore.read(BlockPlacement.configName(id), BlockPlacement.class, null));
    }

    public void delete(String id) {
        configStore.delete(BlockPlacement.configName(id));
    }

    /** Every placement in every theme. */
    public List<BlockPlacement> all() {
        List<BlockPlacement> placements = new ArrayList<>();
        for (String name : configStore.listNames(BlockPlacement.CONFIG_PREFIX)) {
            find(name.substring(BlockPlacement.CONFIG_PREFIX.length() + 1)).ifPresent(placements::add);
        }
        return placements;
    }

    /** The placements in one theme, in drawing order. */
    public List<BlockPlacement> inTheme(String theme) {
        return all().stream()
                .filter(placement -> placement.theme().equals(theme))
                .sorted(DRAWING_ORDER)
                .toList();
    }
}
