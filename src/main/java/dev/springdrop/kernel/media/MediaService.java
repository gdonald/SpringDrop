package dev.springdrop.kernel.media;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.EntityValidationException;
import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldConstraintProvider;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.display.FieldDisplaySlot;
import dev.springdrop.kernel.field.display.ViewDisplayConfig;
import dev.springdrop.kernel.field.display.ViewDisplayManager;
import dev.springdrop.kernel.path.PathAliasManager;
import dev.springdrop.kernel.plugin.PluginManager;
import dev.springdrop.kernel.plugin.PluginRegistry;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.theme.ThemeService;
import dev.springdrop.kernel.validation.ConstraintViolation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Creating media types and storing, finding, and drawing media.
 *
 * <p>Saving a media item reads its source value through the type's media
 * source when the value is new or changed, or the item has no thumbnail yet:
 * the thumbnail comes from the source, and so does the name when the item has
 * none. A value the source cannot use stops the save with the source's reason,
 * bound to the source field.
 */
@Component
public class MediaService {

    public static final String TEMPLATE_DIRECTORY = "media";

    /** The longest name a media item takes. */
    public static final int MAX_NAME_LENGTH = 255;

    private final EntityCrudService entities;
    private final EntityQueryExecutor queries;
    private final EntityTypeManager entityTypeManager;
    private final MediaTypeManager types;
    private final PluginRegistry registry;
    private final FieldConfigManager fields;
    private final ViewDisplayManager displays;
    private final ThemeService themes;
    private final Clock clock;
    private final PathAliasManager aliases;

    public MediaService(EntityCrudService entities, EntityQueryExecutor queries, EntityTypeManager entityTypeManager,
            MediaTypeManager types, PluginRegistry registry, FieldConfigManager fields, ViewDisplayManager displays,
            ThemeService themes, Clock clock, PathAliasManager aliases) {
        this.entities = entities;
        this.queries = queries;
        this.entityTypeManager = entityTypeManager;
        this.types = types;
        this.registry = registry;
        this.fields = fields;
        this.displays = displays;
        this.themes = themes;
        this.clock = clock;
        this.aliases = aliases;
    }

    /** Creates the tables media are stored in, as the application starts. */
    @EventListener(ApplicationReadyEvent.class)
    public void install() {
        entityTypeManager.installStorage(MediaEntityType.ID);
    }

    public PluginManager<MediaSource> sources() {
        return registry.managerFor(MediaSource.class);
    }

    public MediaSource sourceOf(MediaType type) {
        return sources().get(type.source());
    }

    /**
     * Saves a new media type with its source field: a required field of the
     * type the source names, set up as the source says, shown with the
     * source's formatter.
     */
    public MediaType createType(String id, String label, String description, String sourceId) {
        MediaSource source = sources().get(sourceId);
        MediaType type = new MediaType(id, label, description, sourceId, MediaType.sourceFieldFor(id));
        types.save(type);
        fields.createStorage(new FieldStorageConfig(type.sourceField(), MediaEntityType.ID,
                source.sourceFieldType(), 1, source.sourceFieldStorageSettings()));
        fields.createInstance(FieldInstanceConfig.of(type.sourceField(), MediaEntityType.ID, id, source.label())
                .asRequired().withSettings(source.sourceFieldInstanceSettings()));
        displays.save(ViewDisplayConfig.of(MediaEntityType.ID, id, ViewDisplayConfig.DEFAULT_MODE)
                .with(FieldDisplaySlot.of(type.sourceField(), source.sourceFormatter(), 0)));
        return type;
    }

    /** Removes a media type with its fields and displays, once it has no media. */
    public void deleteType(MediaType type) {
        fields.fieldNames(MediaEntityType.ID, type.id())
                .forEach(field -> fields.deleteInstance(MediaEntityType.ID, type.id(), field));
        fields.findStorage(MediaEntityType.ID, type.sourceField())
                .ifPresent(storage -> fields.deleteStorage(MediaEntityType.ID, storage.name()));
        displays.delete(MediaEntityType.ID, type.id(), ViewDisplayConfig.DEFAULT_MODE);
        types.delete(type.id());
    }

    /** Whether any media item is of the type, which keeps the type from being deleted. */
    public boolean inUse(String typeId) {
        return queries.query(MediaEntityType.ID).condition(Condition.equal(MediaEntityType.BUNDLE_KEY, typeId))
                .count() > 0;
    }

    /** A media item not yet saved, published and owned by the given account. */
    public EntityData create(MediaType type, long ownerId) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(BaseFieldDefinition.STATUS, true);
        values.put(BaseFieldDefinition.OWNER, ownerId);
        return EntityData.of(MediaEntityType.ID, null, type.id(), "", values);
    }

    public EntityData save(EntityData media) {
        return save(media, true);
    }

    public EntityData save(EntityData media, boolean newRevision) {
        MediaType type = types.find(media.bundle())
                .orElseThrow(() -> new IllegalArgumentException("No media type " + media.bundle()));
        Map<String, Object> values = new LinkedHashMap<>(media.fields());
        String name = media.label().strip();
        Object sourceValue = single(values.get(type.sourceField()));
        if (sourceValue != null && (name.isEmpty() || values.get(MediaEntityType.THUMBNAIL) == null
                || sourceChanged(media, type, sourceValue))) {
            SourceMetadata metadata = metadata(type, sourceValue);
            values.put(MediaEntityType.THUMBNAIL, metadata.thumbnail());
            if (name.isEmpty()) {
                name = metadata.name();
            }
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        values.putIfAbsent(BaseFieldDefinition.CREATED, now);
        values.put(BaseFieldDefinition.CHANGED, now);
        String label = (name.length() > MAX_NAME_LENGTH) ? name.substring(0, MAX_NAME_LENGTH) : name;
        return entities.save(new EntityData(MediaEntityType.ID, media.id(), media.uuid(), media.bundle(), label,
                media.langcode(), null, values), newRevision);
    }

    private SourceMetadata metadata(MediaType type, Object sourceValue) {
        try {
            return sourceOf(type).metadata(sourceValue);
        } catch (MediaSourceException refused) {
            throw new EntityValidationException(MediaEntityType.ID, List.of(new ConstraintViolation(
                    FieldConstraintProvider.FIELD_PATH_PREFIX + type.sourceField(), refused.getMessage())));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private boolean sourceChanged(EntityData media, MediaType type, Object sourceValue) {
        return media.id() == null || find(((Number) media.id()).longValue())
                .map(stored -> !Objects.equals(single(stored.fields().get(type.sourceField())), sourceValue))
                .orElse(true);
    }

    /** The first value of a field, whether it holds one value or a list of them. */
    static Object single(Object value) {
        if (value instanceof List<?> list) {
            return list.isEmpty() ? null : list.getFirst();
        }
        return value;
    }

    public Optional<EntityData> find(long id) {
        return entities.load(MediaEntityType.ID, id);
    }

    /** Deletes the item and the aliases of its page. */
    public void delete(long id) {
        entities.delete(MediaEntityType.ID, id);
        aliases.deleteAll(MediaEntityType.path(id));
    }

    /** The media item drawn in a view mode through its themed template. */
    public Renderable build(EntityData media, String viewMode, boolean page) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("id", media.id());
        variables.put("label", media.label());
        variables.put("url", aliases.outbound(MediaEntityType.path(media.id())));
        variables.put("viewMode", viewMode);
        variables.put("page", page);
        variables.put("published", Boolean.TRUE.equals(media.fields().get(BaseFieldDefinition.STATUS)));
        return themes.entity(TEMPLATE_DIRECTORY, MediaEntityType.ID, media.bundle(), viewMode,
                        String.valueOf(media.id()), variables)
                .child(Renderable.of("markup").with("value", displays.render(media, viewMode)))
                .cacheTag(MediaEntityType.ID + ":" + media.id());
    }
}
