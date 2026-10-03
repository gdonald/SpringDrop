package dev.springdrop.kernel.media;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.schema.ColumnType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Media: images, documents, audio, video, and remote video, each a reusable
 * item that content refers to. Media come in media types, each drawing its item
 * from one media source, and every save keeps a revision. The entity's label is
 * the media item's name, and its thumbnail is a managed image file.
 */
@Component
public class MediaEntityType implements EntityTypeProvider {

    public static final String ID = "media";

    public static final String TYPE_ID = "media_type";

    public static final String BUNDLE_KEY = "bundle";

    /** The managed image file standing for the item in lists and the library. */
    public static final String THUMBNAIL = "thumbnail";

    public static String path(Object id) {
        return "/media/" + id;
    }

    public static String editPath(Object id) {
        return path(id) + "/edit";
    }

    public static EntityType definition() {
        List<BaseFieldDefinition> baseFields = new ArrayList<>(BaseFieldDefinition.authoredContent());
        baseFields.add(BaseFieldDefinition.optional(THUMBNAIL, ColumnType.BIGINT));
        return EntityType.content(ID, EntityData.class)
                .withBundles(BUNDLE_KEY, TYPE_ID)
                .withRevisions()
                .withBaseFields(baseFields)
                .withAccessHandler(MediaAccessHandler.class)
                .withLinks(Map.of("canonical", "/media/{media}", "edit-form", "/media/{media}/edit"));
    }

    @Override
    public List<EntityType> entityTypes() {
        return List.of(definition(), EntityType.config(TYPE_ID, MediaType.class));
    }
}
