package dev.springdrop.kernel.search;

import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.types.FormattedText;
import dev.springdrop.kernel.field.types.StringFieldType;
import dev.springdrop.kernel.field.types.StringLongFieldType;
import dev.springdrop.kernel.field.types.TextFieldType;
import dev.springdrop.kernel.field.types.TextLongFieldType;
import dev.springdrop.kernel.field.types.TextWithSummaryFieldType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jsoup.Jsoup;

/**
 * A document of an entity's label and the text of its plain and formatted text
 * fields, with HTML tags removed. A summary is indexed with its text.
 */
public class TextFieldsDocumentBuilder implements SearchDocumentBuilder {

    static final Set<String> PLAIN_TYPES = Set.of(StringFieldType.ID, StringLongFieldType.ID);

    static final Set<String> FORMATTED_TYPES = Set.of(TextFieldType.ID, TextLongFieldType.ID,
            TextWithSummaryFieldType.ID);

    private final String entityType;
    private final FieldConfigManager fields;

    public TextFieldsDocumentBuilder(String entityType, FieldConfigManager fields) {
        this.entityType = entityType;
        this.fields = fields;
    }

    @Override
    public String entityType() {
        return entityType;
    }

    @Override
    public SearchDocument build(EntityData entity) {
        List<String> parts = new ArrayList<>();
        for (FieldStorageConfig storage : fields.storages(entityType)) {
            boolean formatted = FORMATTED_TYPES.contains(storage.type());
            if (!formatted && !PLAIN_TYPES.contains(storage.type())) {
                continue;
            }
            for (Object value : values(entity.fields().get(storage.name()))) {
                if (formatted) {
                    parts.add(FormattedText.part(value, FormattedText.SUMMARY));
                    parts.add(FormattedText.part(value, FormattedText.VALUE));
                } else {
                    parts.add(String.valueOf(value));
                }
            }
        }
        String body = parts.stream().map(part -> Jsoup.parse(part).text()).filter(text -> !text.isEmpty())
                .reduce((first, second) -> first + "\n" + second).orElse("");
        return new SearchDocument(entityType, ((Number) entity.id()).longValue(), entity.langcode(),
                entity.label(), body);
    }

    private static List<?> values(Object stored) {
        if (stored == null) {
            return List.of();
        }
        return (stored instanceof List<?> list) ? list : List.of(stored);
    }
}
