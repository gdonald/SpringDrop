package dev.springdrop.kernel.file;

import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.plugin.SpringDropPlugin;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.validation.Constraint;
import dev.springdrop.kernel.validation.ValidationContext;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Requires a file or image value to point at a stored file within the field's
 * limits: an allowed extension, no larger than the largest size, and for an
 * image, one the site can read whose pixel size is within bounds and which
 * carries alternative text when that is required.
 *
 * <p>Saved in a request, the file has to be either one the entity already uses
 * or a temporary upload of the person saving, so a form cannot attach a file
 * someone else uploaded. A save made outside a request is not asked who is
 * saving. A missing value passes.
 */
@SpringDropPlugin(id = FileItemConstraint.ID, type = Constraint.class)
public class FileItemConstraint implements Constraint {

    public static final String ID = "file_item";

    /** Owns uploads made by someone not signed in. */
    public static final long ANONYMOUS_OWNER = 0L;

    private final ObjectProvider<FileService> files;
    private final ObjectProvider<FileUsageService> usage;

    public FileItemConstraint(ObjectProvider<FileService> files, ObjectProvider<FileUsageService> usage) {
        this.files = files;
        this.usage = usage;
    }

    @Override
    public Optional<String> validate(Object value, Map<String, Object> options, ValidationContext context) {
        if (value == null) {
            return Optional.empty();
        }
        Optional<ManagedFile> found = FileItem.fileId(value).flatMap(id -> files.getObject().find(id));
        if (found.isEmpty()) {
            return Optional.of("Choose a file that has been uploaded.");
        }
        ManagedFile file = found.get();
        if (!mayAttach(file, context.subject())) {
            return Optional.of("You may not use this file.");
        }
        UploadLimits limits = UploadLimits.fromOptions(options);
        Optional<String> violation = limits.violation(file.filename(), file.size())
                .or(() -> limits.imageViolation(() -> files.getObject().imageSize(file)));
        if (violation.isPresent()) {
            return violation;
        }
        if (FileItem.part(value, FileItem.DESCRIPTION).length() > FileItem.MAX_DESCRIPTION_LENGTH) {
            return Optional.of("The description is longer than " + FileItem.MAX_DESCRIPTION_LENGTH + " characters.");
        }
        if (FileItem.part(value, FileItem.ALT).length() > FileItem.MAX_IMAGE_TEXT_LENGTH
                || FileItem.part(value, FileItem.TITLE).length() > FileItem.MAX_IMAGE_TEXT_LENGTH) {
            return Optional.of("Alternative text and titles are at most " + FileItem.MAX_IMAGE_TEXT_LENGTH
                    + " characters.");
        }
        if (Boolean.TRUE.equals(options.get(FileItem.ALT_FIELD_REQUIRED)) && FileItem.part(value, FileItem.ALT).isBlank()) {
            return Optional.of("Alternative text is required.");
        }
        return Optional.empty();
    }

    private boolean mayAttach(ManagedFile file, Object subject) {
        Authentication saving = SecurityContextHolder.getContext().getAuthentication();
        if (saving == null) {
            return true;
        }
        if (!file.permanent() && file.owner() == accountId(saving)) {
            return true;
        }
        return subject instanceof EntityData entity && entity.id() != null
                && usage.getObject().filesUsedBy(FileUsageTracker.MODULE, entity.entityType(), entity.id())
                        .contains(file.id());
    }

    private static long accountId(Authentication authentication) {
        return (authentication.getPrincipal() instanceof AccountPrincipal account) ? account.id() : ANONYMOUS_OWNER;
    }
}
