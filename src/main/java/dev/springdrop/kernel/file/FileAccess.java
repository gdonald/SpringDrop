package dev.springdrop.kernel.file;

import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityKind;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.user.AccountPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Who may download a private file. The uploader of a temporary file may, so a
 * form can show what was just uploaded. Otherwise someone may download a file
 * when they may view any content entity using it, so a file is as private as
 * the content it is attached to.
 */
@Component
public class FileAccess {

    private final FileUsageService usage;
    private final EntityTypeManager entityTypeManager;
    private final EntityCrudService entities;

    public FileAccess(FileUsageService usage, EntityTypeManager entityTypeManager, EntityCrudService entities) {
        this.usage = usage;
        this.entityTypeManager = entityTypeManager;
        this.entities = entities;
    }

    public boolean mayDownload(ManagedFile file, Authentication authentication) {
        if (!file.permanent()) {
            return authentication != null && authentication.getPrincipal() instanceof AccountPrincipal account
                    && account.id() == file.owner();
        }
        return usage.usages(file.id()).stream().anyMatch(use -> mayView(use.type(), use.id(), authentication));
    }

    private boolean mayView(String entityTypeId, String id, Authentication authentication) {
        return entityTypeManager.find(entityTypeId)
                .filter(type -> type.kind() == EntityKind.CONTENT && id.matches("\\d{1,18}"))
                .flatMap(type -> entities.load(type.id(), Long.valueOf(id))
                        .map(entity -> entityTypeManager.accessHandlerFor(type.id())
                                .check(type, entity, EntityAccessHandler.VIEW, authentication).allowed()))
                .orElse(false);
    }
}
