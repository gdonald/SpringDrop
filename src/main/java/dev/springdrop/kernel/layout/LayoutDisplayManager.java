package dev.springdrop.kernel.layout;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockInstance;
import dev.springdrop.kernel.block.plugins.FieldBlock;
import dev.springdrop.kernel.block.plugins.FieldBlockDeriver;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.display.FieldDisplaySlot;
import dev.springdrop.kernel.field.display.ViewDisplayConfig;
import dev.springdrop.kernel.field.display.ViewDisplayManager;
import dev.springdrop.kernel.layout.layouts.OneColumnLayout;
import dev.springdrop.kernel.render.Renderable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Which view modes Layout Builder draws, and drawing an entity through one.
 *
 * <p>Where the view mode allows it, an entity may carry a layout of its own in
 * the {@link #OVERRIDE_FIELD} base field, which is drawn in place of its
 * bundle's. The field lives in the entity's base and revision tables, so each
 * revision keeps the layout it had, and every translation of the entity shares
 * the one layout. An empty override means the entity uses its bundle's.
 *
 * <p>Turning Layout Builder on for a view mode that has never had a layout
 * starts it with one single-column section holding a field block for each field
 * the view display shows, in the same order and through the same formatters, so
 * the entity reads as it did until the layout is changed.
 */
@Component
public class LayoutDisplayManager {

    /** The base field an entity's own layout is stored in, by an entity type that allows one. */
    public static final String OVERRIDE_FIELD = "layout_builder__layout";

    private final ConfigStore configStore;
    private final ViewDisplayManager viewDisplays;
    private final FieldConfigManager fields;
    private final SectionRenderer sections;
    private final ObjectMapper objectMapper;

    public LayoutDisplayManager(
            ConfigStore configStore,
            ViewDisplayManager viewDisplays,
            FieldConfigManager fields,
            SectionRenderer sections,
            ObjectMapper objectMapper) {
        this.configStore = configStore;
        this.viewDisplays = viewDisplays;
        this.fields = fields;
        this.sections = sections;
        this.objectMapper = objectMapper;
    }

    public Optional<LayoutBuilderDisplay> find(String entityTypeId, String bundle, String mode) {
        return Optional.ofNullable(configStore.read(
                LayoutBuilderDisplay.configName(entityTypeId, bundle, mode), LayoutBuilderDisplay.class, null));
    }

    public void save(LayoutBuilderDisplay display) {
        configStore.save(
                LayoutBuilderDisplay.configName(display.entityTypeId(), display.bundle(), display.mode()), display);
    }

    public void delete(String entityTypeId, String bundle, String mode) {
        configStore.delete(LayoutBuilderDisplay.configName(entityTypeId, bundle, mode));
    }

    /** Turns Layout Builder on, keeping any layout the view mode had before it was turned off. */
    public LayoutBuilderDisplay enable(String entityTypeId, String bundle, String mode) {
        LayoutBuilderDisplay display = find(entityTypeId, bundle, mode)
                .orElseGet(() -> LayoutBuilderDisplay.of(entityTypeId, bundle, mode,
                        List.of(fieldsAsTheyRead(entityTypeId, bundle, mode))))
                .withEnabled(true);
        save(display);
        return display;
    }

    /** Turns Layout Builder off, keeping the layout so turning it back on restores it. */
    public void disable(String entityTypeId, String bundle, String mode) {
        find(entityTypeId, bundle, mode).ifPresent(display -> save(display.withEnabled(false)));
    }

    /**
     * The entity drawn by its own layout when the view mode allows one and it has
     * one, else by its bundle's layout, or nothing when Layout Builder does not
     * draw that view mode.
     */
    public Optional<Renderable> render(EntityData entity, String mode, BlockContext context) {
        return enabledDisplay(entity, mode).map(display -> sections.render(
                        display.allowOverrides() ? overrideOf(entity).orElse(display.sections()) : display.sections(),
                        context.withRouteEntity(entity))
                .cacheTag(display.cacheTag()));
    }

    /** The view mode's layout when Layout Builder draws it, for the entity's bundle. */
    public Optional<LayoutBuilderDisplay> enabledDisplay(EntityData entity, String mode) {
        String bundle = (entity.bundle() == null) ? entity.entityType() : entity.bundle();
        return find(entity.entityType(), bundle, mode).filter(LayoutBuilderDisplay::enabled);
    }

    /** The layout the entity carries of its own, if it has one. */
    public Optional<List<Section>> overrideOf(EntityData entity) {
        Object stored = entity.fields().get(OVERRIDE_FIELD);
        if (stored == null) {
            return Optional.empty();
        }
        List<Section> override = List.of(objectMapper.convertValue(stored, Section[].class));
        return override.isEmpty() ? Optional.empty() : Optional.of(override);
    }

    /** The entity carrying the given layout of its own, which saving it stores. */
    public EntityData withOverride(EntityData entity, List<Section> override) {
        Map<String, Object> values = new LinkedHashMap<>(entity.fields());
        values.put(OVERRIDE_FIELD, override);
        return entity.withFields(values);
    }

    /** The entity back on its bundle's layout, which saving it stores. */
    public EntityData withoutOverride(EntityData entity) {
        return withOverride(entity, List.of());
    }

    private Section fieldsAsTheyRead(String entityTypeId, String bundle, String mode) {
        Optional<ViewDisplayConfig> display = viewDisplays.find(entityTypeId, bundle, mode);
        Section section = Section.of(OneColumnLayout.ID);
        List<String> shown = viewDisplays.shownFields(entityTypeId, bundle, mode);
        for (int position = 0; position < shown.size(); position++) {
            String field = shown.get(position);
            section = section.withComponent(new SectionComponent(
                    OneColumnLayout.CONTENT, position, fieldBlock(entityTypeId, bundle, field, display)));
        }
        return section;
    }

    private BlockInstance fieldBlock(
            String entityTypeId, String bundle, String field, Optional<ViewDisplayConfig> display) {

        String label = fields.findInstance(entityTypeId, bundle, field).orElseThrow().label();
        boolean labelShown = display.map(layout -> layout.showsLabelOf(field)).orElse(true);
        Map<String, Object> settings = display.map(layout -> layout.slots().get(field))
                .map(FieldDisplaySlot::handler)
                .<Map<String, Object>>map(formatter -> Map.of(FieldBlock.FORMATTER, formatter))
                .orElse(Map.of());
        return new BlockInstance(UUID.randomUUID().toString(), FieldBlockDeriver.blockId(entityTypeId, bundle, field),
                label, labelShown, settings);
    }
}
