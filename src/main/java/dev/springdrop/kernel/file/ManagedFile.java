package dev.springdrop.kernel.file;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import java.time.OffsetDateTime;

/** One managed file as the rest of the site reads it. */
public record ManagedFile(
        long id, String uri, String filename, String mime, long size, long owner, boolean permanent,
        OffsetDateTime changed) {

    public static ManagedFile of(EntityData file) {
        return new ManagedFile(
                ((Number) file.id()).longValue(),
                String.valueOf(file.fields().get(FileEntityType.URI)),
                file.label(),
                String.valueOf(file.fields().getOrDefault(FileEntityType.MIME, "application/octet-stream")),
                ((Number) file.fields().getOrDefault(FileEntityType.SIZE, 0L)).longValue(),
                ((Number) file.fields().getOrDefault(BaseFieldDefinition.OWNER, 0L)).longValue(),
                Boolean.TRUE.equals(file.fields().get(BaseFieldDefinition.STATUS)),
                (OffsetDateTime) file.fields().get(BaseFieldDefinition.CHANGED));
    }

    /** The scheme the uri starts with. */
    public String scheme() {
        return uri.substring(0, uri.indexOf("://"));
    }

    /** The path after the scheme. */
    public String path() {
        return uri.substring(uri.indexOf("://") + 3);
    }
}
