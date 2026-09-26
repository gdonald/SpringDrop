package dev.springdrop.kernel.block.content;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockPlugin;
import dev.springdrop.kernel.block.BlockSettings;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.render.CacheMetadata;
import dev.springdrop.kernel.render.Renderable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A custom block from the library, drawn with its fields. The placement names
 * which one, so the same custom block can be placed in several themes and
 * regions and edited once.
 */
@SpringDropPlugin(id = BlockContentBlock.ID, type = BlockPlugin.class)
public class BlockContentBlock implements BlockPlugin {

    public static final String ID = "block_content";

    public static final String BLOCK = "block";

    public static final String BLOCK_ELEMENT = SETTINGS_PREFIX + BLOCK;

    public static final String UNKNOWN_BLOCK_MESSAGE = "Choose a custom block from the library.";

    private final BlockContentService library;

    public BlockContentBlock(BlockContentService library) {
        this.library = library;
    }

    @Override
    public String label() {
        return "Custom block";
    }

    @Override
    public Optional<Renderable> build(BlockContext context, Map<String, Object> settings) {
        return blockId(BlockSettings.string(settings, BLOCK))
                .flatMap(library::find)
                .map(block -> Renderable.of("markup").with("value", library.render(block)));
    }

    @Override
    public CacheMetadata cacheability(Map<String, Object> settings) {
        return blockId(BlockSettings.string(settings, BLOCK))
                .map(id -> CacheMetadata.EMPTY.withTag(BlockContentService.cacheTag(id)))
                .orElse(CacheMetadata.EMPTY);
    }

    @Override
    public List<FormElement> settingsForm(Map<String, Object> settings) {
        return List.of(FormElement.of(ElementType.SELECT, BLOCK_ELEMENT)
                .label("Custom block")
                .markRequired()
                .value(BlockSettings.string(settings, BLOCK))
                .options(library.all().stream()
                        .map(block -> new SelectOption(String.valueOf(block.id()), block.label()))
                        .toList()));
    }

    @Override
    public Map<String, Object> settingsValues(Map<String, String> submitted) {
        return Map.of(BLOCK, submitted.getOrDefault(BLOCK_ELEMENT, ""));
    }

    /** The chosen block has to be one the library holds, whatever the browser sent. */
    @Override
    public Map<String, String> validateSettings(Map<String, String> submitted) {
        boolean known = blockId(submitted.getOrDefault(BLOCK_ELEMENT, ""))
                .flatMap(library::find)
                .isPresent();
        return known ? Map.of() : Map.of(BLOCK_ELEMENT, UNKNOWN_BLOCK_MESSAGE);
    }

    private static Optional<Long> blockId(String value) {
        return value.matches("\\d{1,18}") ? Optional.of(Long.parseLong(value)) : Optional.empty();
    }
}
