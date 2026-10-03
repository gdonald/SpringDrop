package dev.springdrop.kernel.media;

import dev.springdrop.kernel.config.ConfigStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** The media types the site has, each stored as the config entity {@code media_type.<id>}. */
@Component
public class MediaTypeManager {

    private final ConfigStore configStore;

    public MediaTypeManager(ConfigStore configStore) {
        this.configStore = configStore;
    }

    public static String configName(String id) {
        return MediaEntityType.TYPE_ID + "." + id;
    }

    public void save(MediaType type) {
        configStore.save(configName(type.id()), type);
    }

    public Optional<MediaType> find(String id) {
        return Optional.ofNullable(configStore.read(configName(id), MediaType.class, null));
    }

    public void delete(String id) {
        configStore.delete(configName(id));
    }

    /** Every media type, by label. */
    public List<MediaType> all() {
        List<MediaType> types = new ArrayList<>();
        for (String name : configStore.listNames(MediaEntityType.TYPE_ID)) {
            find(name.substring(MediaEntityType.TYPE_ID.length() + 1)).ifPresent(types::add);
        }
        return types.stream().sorted(Comparator.comparing(MediaType::label).thenComparing(MediaType::id)).toList();
    }
}
