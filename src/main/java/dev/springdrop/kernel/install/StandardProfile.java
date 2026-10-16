package dev.springdrop.kernel.install;

import dev.springdrop.kernel.moderation.ContentModerationService;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The site most people start from: Article and Basic page content types with
 * their fields and displays, a Tags vocabulary, comments on articles, the four
 * menus, blocks in the front-end theme's regions, and roles with permissions
 * for visitors, editors, and administrators. Its config is under
 * {@code springdrop/profiles/standard/config}. The editorial workflow is
 * created and left off every content type.
 */
@Component
public class StandardProfile implements InstallProfile {

    public static final String ID = "standard";

    private final ContentModerationService moderation;

    public StandardProfile(ContentModerationService moderation) {
        this.moderation = moderation;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Standard";
    }

    @Override
    public String description() {
        return "Start with articles, basic pages, tags, comments, and the roles most sites use.";
    }

    @Override
    public int weight() {
        return 0;
    }

    @Override
    public List<String> modules() {
        return List.of();
    }

    @Override
    public void install() {
        moderation.installEditorial();
    }
}
