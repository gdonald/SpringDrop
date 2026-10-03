package dev.springdrop.kernel.media;

import dev.springdrop.kernel.permission.PermissionDefinition;
import dev.springdrop.kernel.permission.PermissionProvider;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The permissions media are gated by: the site-wide ones declared in
 * {@code media.permissions.yml}, and for each media type, creating its media
 * and editing and deleting them, one's own or anyone's.
 */
@Component
public class MediaPermissions implements PermissionProvider {

    public static final String PROVIDER = "media";

    /** Viewing, editing, and deleting every media item, published or not. */
    public static final String ADMINISTER_MEDIA = "administer media";

    /** Adding media types and choosing their sources. */
    public static final String ADMINISTER_MEDIA_TYPES = "administer media types";

    /** Viewing published media. */
    public static final String VIEW_MEDIA = "view media";

    /** Viewing one's own media while it is unpublished. */
    public static final String VIEW_OWN_UNPUBLISHED = "view own unpublished media";

    /** Listing media in the library and in the media overview. */
    public static final String ACCESS_MEDIA_OVERVIEW = "access media overview";

    private final MediaTypeManager types;

    public MediaPermissions(MediaTypeManager types) {
        this.types = types;
    }

    public static String create(String type) {
        return "create " + type + " media";
    }

    public static String editOwn(String type) {
        return "edit own " + type + " media";
    }

    public static String editAny(String type) {
        return "edit any " + type + " media";
    }

    public static String deleteOwn(String type) {
        return "delete own " + type + " media";
    }

    public static String deleteAny(String type) {
        return "delete any " + type + " media";
    }

    @Override
    public List<PermissionDefinition> permissions() {
        List<PermissionDefinition> permissions = new ArrayList<>();
        for (MediaType type : types.all()) {
            String label = type.label();
            permissions.add(PermissionDefinition.of(create(type.id()), label + ": Create new media", PROVIDER));
            permissions.add(PermissionDefinition.of(editOwn(type.id()), label + ": Edit own media", PROVIDER));
            permissions.add(PermissionDefinition.of(editAny(type.id()), label + ": Edit any media", PROVIDER));
            permissions.add(PermissionDefinition.of(deleteOwn(type.id()), label + ": Delete own media", PROVIDER));
            permissions.add(PermissionDefinition.of(deleteAny(type.id()), label + ": Delete any media", PROVIDER));
        }
        return permissions;
    }
}
