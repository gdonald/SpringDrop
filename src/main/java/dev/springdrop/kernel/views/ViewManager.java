package dev.springdrop.kernel.views;

import dev.springdrop.kernel.config.ConfigStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** The site's views, each stored as the config object {@code views.view.<id>}. */
@Component
public class ViewManager {

    private final ConfigStore configStore;

    public ViewManager(ConfigStore configStore) {
        this.configStore = configStore;
    }

    public void save(ViewConfig view) {
        configStore.save(ViewConfig.configName(view.id()), view);
    }

    public Optional<ViewConfig> find(String id) {
        return Optional.ofNullable(configStore.read(ViewConfig.configName(id), ViewConfig.class, null));
    }

    public void delete(String id) {
        configStore.delete(ViewConfig.configName(id));
    }

    /** Every view, by label. */
    public List<ViewConfig> all() {
        List<ViewConfig> views = new ArrayList<>();
        for (String name : configStore.listNames(ViewConfig.CONFIG_PREFIX)) {
            find(name.substring(ViewConfig.CONFIG_PREFIX.length() + 1)).ifPresent(views::add);
        }
        return views.stream().sorted(Comparator.comparing(ViewConfig::label)).toList();
    }
}
