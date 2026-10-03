package dev.springdrop.kernel.file;

import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.FieldTableStorage;
import dev.springdrop.kernel.event.EntityEvent;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.FormattedText;
import dev.springdrop.kernel.field.types.TextFieldType;
import dev.springdrop.kernel.field.types.TextLongFieldType;
import dev.springdrop.kernel.field.types.TextWithSummaryFieldType;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Keeps file usage in step with saved entities. An entity uses every file any
 * of its file or image fields points at, recorded under the module {@code file},
 * and every public image its formatted text inserts through an editor, marked
 * with a {@code data-file-id} attribute, recorded under the module
 * {@code editor}. Both count what the entity holds in any language and in any
 * revision it keeps, so going back to an older revision finds the files it had.
 * Deleting the entity releases every file it used.
 *
 * <p>Only public files count for formatted text, since a private file's access
 * follows whatever uses it, and text could name any file's id.
 */
@Component
public class FileUsageTracker {

    /** The module file and image fields record their usage under. */
    public static final String MODULE = "file";

    /** The module images inserted into formatted text record their usage under. */
    public static final String EDITOR_MODULE = "editor";

    private static final Set<String> FILE_FIELD_TYPES = Set.of(FileFieldType.ID, ImageFieldType.ID);

    private static final Set<String> FORMATTED_TEXT_TYPES = Set.of(TextFieldType.ID, TextLongFieldType.ID,
            TextWithSummaryFieldType.ID);

    private static final Pattern EDITOR_FILE = Pattern.compile("data-file-id=[\"']?(\\d{1,18})");

    private final FieldConfigManager fields;
    private final FieldTableStorage fieldTables;
    private final EntityTypeManager entityTypeManager;
    private final FileUsageService usage;
    private final FileService files;

    public FileUsageTracker(FieldConfigManager fields, FieldTableStorage fieldTables,
            EntityTypeManager entityTypeManager, FileUsageService usage, FileService files) {
        this.fields = fields;
        this.fieldTables = fieldTables;
        this.entityTypeManager = entityTypeManager;
        this.usage = usage;
        this.files = files;
    }

    @EventListener
    public void onEntityEvent(EntityEvent event) {
        if (!(event.entity() instanceof EntityData entity) || entity.entityType().equals(FileEntityType.ID)) {
            return;
        }
        boolean deleted = event.phase() == EntityEvent.Phase.DELETE;
        if (deleted || event.phase() == EntityEvent.Phase.INSERT || event.phase() == EntityEvent.Phase.UPDATE) {
            synchronize(entity, MODULE, deleted ? Set.of() : referenced(entity, FILE_FIELD_TYPES, FileItem::fileIds));
            synchronize(entity, EDITOR_MODULE, deleted ? Set.of()
                    : publicOnly(referenced(entity, FORMATTED_TEXT_TYPES, FileUsageTracker::editorFileIds)));
        }
    }

    private Set<Long> referenced(EntityData entity, Set<String> fieldTypes, Function<Object, List<Long>> fileIds) {
        Set<Long> referenced = new LinkedHashSet<>();
        for (FieldStorageConfig storage : fields.storages(entity.entityType())) {
            if (fieldTypes.contains(storage.type())) {
                fieldTables.everyValue(entityTypeManager.require(entity.entityType()), entity.id(), storage.name())
                        .forEach(value -> referenced.addAll(fileIds.apply(value)));
            }
        }
        return referenced;
    }

    /** The files a formatted text value's text and summary insert. */
    static List<Long> editorFileIds(Object value) {
        List<Long> ids = new ArrayList<>();
        for (String part : List.of(FormattedText.part(value, FormattedText.VALUE),
                FormattedText.part(value, FormattedText.SUMMARY))) {
            Matcher matcher = EDITOR_FILE.matcher(part);
            while (matcher.find()) {
                ids.add(Long.valueOf(matcher.group(1)));
            }
        }
        return ids;
    }

    private Set<Long> publicOnly(Set<Long> fileIds) {
        Set<Long> kept = new LinkedHashSet<>();
        fileIds.forEach(id -> files.find(id).filter(file -> file.scheme().equals(FileSchemes.PUBLIC))
                .ifPresent(file -> kept.add(id)));
        return kept;
    }

    private void synchronize(EntityData entity, String module, Set<Long> referenced) {
        List<Long> recorded = usage.filesUsedBy(module, entity.entityType(), entity.id());
        for (Long fileId : referenced) {
            if (!recorded.contains(fileId)) {
                usage.add(fileId, module, entity.entityType(), entity.id());
            }
        }
        for (Long fileId : recorded) {
            if (!referenced.contains(fileId)) {
                usage.remove(fileId, module, entity.entityType(), entity.id(), true);
            }
        }
    }
}
