package dev.springdrop.kernel.language;

import dev.springdrop.kernel.cache.CacheTagInvalidator;
import dev.springdrop.kernel.cache.CacheTags;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.locale.Translator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Translations of config objects: which objects can be translated, the
 * strings each one holds, and each language's translation of them, kept apart
 * from the object. Saving a translation invalidates the object's cache tag and
 * the {@code locale} tag every page carries.
 */
@Component
public class ConfigTranslationManager {

    private final ConfigStore config;
    private final List<ConfigTranslationMappers> providers;
    private final ObjectMapper objectMapper;
    private final CacheTagInvalidator invalidator;

    public ConfigTranslationManager(ConfigStore config, List<ConfigTranslationMappers> providers,
            ObjectMapper objectMapper, CacheTagInvalidator invalidator) {
        this.config = config;
        this.providers = providers;
        this.objectMapper = objectMapper;
        this.invalidator = invalidator;
    }

    /** One translatable config object: its name, its kind, and what it is called. */
    public record Item(String name, String typeLabel, String label) {
    }

    /** One translatable string of an object: its path, what the form calls it, and its source text. */
    public record SourceString(String path, String label, String text) {
    }

    public List<ConfigTranslationMapper> mappers() {
        return providers.stream().flatMap(provider -> provider.mappers().stream()).toList();
    }

    public Optional<ConfigTranslationMapper> mapperFor(String configName) {
        return mappers().stream().filter(mapper -> mapper.covers(configName)).findFirst();
    }

    /** Every stored object that can be translated, by kind and then name. */
    public List<Item> items() {
        List<Item> items = new ArrayList<>();
        for (ConfigTranslationMapper mapper : mappers()) {
            List<String> names = new ArrayList<>(config.listNames(mapper.prefix()));
            config.readStored(mapper.prefix()).ifPresent(single -> names.add(mapper.prefix()));
            for (String name : names) {
                JsonNode source = config.readStored(name).orElseThrow();
                JsonNode label = source.get("label");
                items.add(new Item(name, mapper.typeLabel(), label == null ? name : label.asString()));
            }
        }
        return items.stream().sorted(Comparator.comparing(Item::typeLabel).thenComparing(Item::name)).toList();
    }

    /** The object's translatable strings as stored, the ones it holds a value for. */
    public List<SourceString> sourceStrings(String configName) {
        ConfigTranslationMapper mapper = mapperFor(configName).orElseThrow(() ->
                new IllegalArgumentException("Config " + configName + " cannot be translated"));
        JsonNode source = config.readStored(configName).orElseThrow(() ->
                new IllegalArgumentException("No config named " + configName));
        List<SourceString> strings = new ArrayList<>();
        for (ConfigTranslationMapper.TranslatableKey key : mapper.keys()) {
            String[] parts = key.path().split("\\.");
            if (key.path().contains(".*.")) {
                JsonNode items = source.get(parts[0]);
                for (JsonNode item : items == null ? List.<JsonNode>of() : items.values()) {
                    JsonNode text = item.get(parts[2]);
                    if (text != null && !text.asString().isEmpty() && item.get("id") != null) {
                        String id = item.get("id").asString();
                        strings.add(new SourceString(parts[0] + "." + id + "." + parts[2],
                                key.label() + " (" + id + ")", text.asString()));
                    }
                }
            } else {
                JsonNode text = source.get(key.path());
                if (text != null && !text.asString().isEmpty()) {
                    strings.add(new SourceString(key.path(), key.label(), text.asString()));
                }
            }
        }
        return strings;
    }

    public Map<String, String> translation(String langcode, String configName) {
        return config.readStored(ConfigTranslation.configName(langcode, configName))
                .map(stored -> objectMapper.convertValue(stored, ConfigTranslation.class).values())
                .orElse(Map.of());
    }

    /** Stores the translated strings, leaving out empty ones, or with none, removes the translation. */
    public void save(String langcode, String configName, Map<String, String> values) {
        Map<String, String> kept = new LinkedHashMap<>();
        values.forEach((path, text) -> {
            if (!text.isBlank()) {
                kept.put(path, text.strip());
            }
        });
        if (kept.isEmpty()) {
            config.delete(ConfigTranslation.configName(langcode, configName));
        } else {
            config.save(ConfigTranslation.configName(langcode, configName), new ConfigTranslation(kept));
        }
        invalidator.invalidate(List.of(CacheTags.config(configName), Translator.CACHE_TAG));
    }

    /**
     * The top-level keys of the object that its translation into the language
     * changes, each with the whole value it takes, or empty when it has none.
     */
    public Optional<Map<String, Object>> overrides(String langcode, String configName) {
        Map<String, String> translated = translation(langcode, configName);
        Optional<JsonNode> stored = config.readStored(configName);
        if (translated.isEmpty() || stored.isEmpty()) {
            return Optional.empty();
        }
        ObjectNode source = (ObjectNode) stored.get().deepCopy();
        Map<String, Object> overrides = new LinkedHashMap<>();
        translated.forEach((path, text) -> {
            String[] parts = path.split("\\.");
            if (parts.length == 1) {
                source.put(parts[0], text);
            } else if (source.get(parts[0]) instanceof ArrayNode items) {
                items.values().forEach(item -> {
                    if (item.get("id") != null && item.get("id").asString().equals(parts[1])) {
                        ((ObjectNode) item).put(parts[2], text);
                    }
                });
            }
            overrides.put(parts[0], objectMapper.convertValue(source.get(parts[0]), Object.class));
        });
        return Optional.of(overrides);
    }
}
