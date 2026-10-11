package dev.springdrop.kernel.entity;

import dev.springdrop.kernel.config.ConfigStore;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Which configurable fields of an entity type are translated, stored as the
 * config object {@code content_translation.<entity type>}. A translated field
 * holds its own values in each translation. Every other field is shared: a
 * translation shows the values of the entity's own language, and saving any
 * translation changes them for all. The label is always translated, and base
 * fields are always shared.
 */
@Component
public class TranslatableFields {

    public static final String CONFIG_PREFIX = "content_translation";

    /** The stored list. */
    public record Settings(List<String> fields) {

        public Settings {
            fields = List.copyOf(fields);
        }
    }

    private final ConfigStore config;

    public TranslatableFields(ConfigStore config) {
        this.config = config;
    }

    public static String configName(String entityTypeId) {
        return CONFIG_PREFIX + "." + entityTypeId;
    }

    public Set<String> of(String entityTypeId) {
        return new LinkedHashSet<>(config.read(configName(entityTypeId), Settings.class, new Settings(List.of()))
                .fields());
    }

    public boolean translatable(String entityTypeId, String field) {
        return of(entityTypeId).contains(field);
    }

    public void set(String entityTypeId, String field, boolean translatable) {
        Set<String> fields = of(entityTypeId);
        if (translatable) {
            fields.add(field);
        } else {
            fields.remove(field);
        }
        config.save(configName(entityTypeId), new Settings(fields.stream().sorted().toList()));
    }
}
