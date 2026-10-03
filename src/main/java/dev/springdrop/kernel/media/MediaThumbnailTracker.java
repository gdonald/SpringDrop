package dev.springdrop.kernel.media;

import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityRevision;
import dev.springdrop.kernel.event.EntityEvent;
import dev.springdrop.kernel.file.FileUsageService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Keeps the thumbnails of media in use, under the module {@code media}: every
 * thumbnail any revision of a media item has, so an older revision keeps its
 * thumbnail. Deleting the item releases them.
 */
@Component
public class MediaThumbnailTracker {

    private final EntityCrudService entities;
    private final FileUsageService usage;

    public MediaThumbnailTracker(EntityCrudService entities, FileUsageService usage) {
        this.entities = entities;
        this.usage = usage;
    }

    @EventListener
    public void onMediaEvent(EntityEvent event) {
        if (!event.entityType().equals(MediaEntityType.ID)) {
            return;
        }
        EntityData media = (EntityData) event.entity();
        boolean deleted = event.phase() == EntityEvent.Phase.DELETE;
        if (deleted || event.phase() == EntityEvent.Phase.INSERT || event.phase() == EntityEvent.Phase.UPDATE) {
            synchronize(media, deleted ? Set.of() : thumbnails(media));
        }
    }

    private Set<Long> thumbnails(EntityData media) {
        Set<Long> thumbnails = new LinkedHashSet<>();
        for (EntityRevision revision : entities.revisions(MediaEntityType.ID, media.id())) {
            if (revision.baseValues().get(MediaEntityType.THUMBNAIL) instanceof Number thumbnail) {
                thumbnails.add(thumbnail.longValue());
            }
        }
        return thumbnails;
    }

    private void synchronize(EntityData media, Set<Long> thumbnails) {
        List<Long> recorded = usage.filesUsedBy(MediaEntityType.ID, MediaEntityType.ID, media.id());
        thumbnails.stream().filter(id -> !recorded.contains(id))
                .forEach(id -> usage.add(id, MediaEntityType.ID, MediaEntityType.ID, media.id()));
        recorded.stream().filter(id -> !thumbnails.contains(id))
                .forEach(id -> usage.remove(id, MediaEntityType.ID, MediaEntityType.ID, media.id(), true));
    }
}
