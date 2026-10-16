package dev.springdrop.kernel.install;

import dev.springdrop.kernel.batch.BatchDefinition;
import dev.springdrop.kernel.batch.BatchManager;
import dev.springdrop.kernel.batch.BatchOperationSpec;
import dev.springdrop.kernel.batch.BatchProgress;
import dev.springdrop.kernel.module.ModuleInfo;
import dev.springdrop.kernel.module.ModuleRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Installs the site through the Batch API: an operation enables each of the
 * profile's modules and the modules they depend on, in dependency order; an
 * operation imports each of the profile's config files, each after the config
 * it depends on; an operation runs the profile's own install step; and a last
 * operation sets the site's language and details,
 * gives the first account its name, address, and password, and marks the
 * site installed.
 */
@Component
public class Installer {

    private final List<InstallProfile> profiles;
    private final ModuleRegistry modules;
    private final BatchManager batches;
    private final ProfileConfig profileConfig;

    public Installer(List<InstallProfile> profiles, ModuleRegistry modules, BatchManager batches,
            ProfileConfig profileConfig) {
        this.profiles = List.copyOf(profiles);
        this.modules = modules;
        this.batches = batches;
        this.profileConfig = profileConfig;
    }

    /** Every profile, by weight. */
    public List<InstallProfile> profiles() {
        return profiles.stream().sorted(Comparator.comparingInt(InstallProfile::weight)).toList();
    }

    public Optional<InstallProfile> profile(String id) {
        return profiles.stream().filter(profile -> profile.id().equals(id)).findFirst();
    }

    /** The details the last operation applies. */
    public record SiteDetails(String langcode, String siteName, String siteMail, String adminName, String adminMail,
            String passwordHash) {
    }

    public BatchProgress start(InstallProfile profile, SiteDetails details, String finishedPath) {
        List<BatchOperationSpec> operations = new ArrayList<>();
        moduleOrder(profile).forEach(module -> operations.add(BatchOperationSpec.of(InstallModuleOperation.ID,
                Map.of(InstallModuleOperation.MODULE, module))));
        profileConfig.objects(profile).keySet().forEach(name -> operations.add(BatchOperationSpec.of(
                InstallConfigOperation.ID, Map.of(InstallConfigOperation.PROFILE, profile.id(),
                        InstallConfigOperation.NAME, name))));
        operations.add(BatchOperationSpec.of(InstallProfileOperation.ID,
                Map.of(InstallProfileOperation.PROFILE, profile.id())));
        Map<String, Object> finish = new LinkedHashMap<>();
        finish.put(InstallFinishOperation.LANGCODE, details.langcode());
        finish.put(InstallFinishOperation.SITE_NAME, details.siteName());
        finish.put(InstallFinishOperation.SITE_MAIL, details.siteMail());
        finish.put(InstallFinishOperation.ADMIN_NAME, details.adminName());
        finish.put(InstallFinishOperation.ADMIN_MAIL, details.adminMail());
        finish.put(InstallFinishOperation.PASSWORD_HASH, details.passwordHash());
        operations.add(BatchOperationSpec.of(InstallFinishOperation.ID, finish));
        return batches.start(BatchDefinition.of("install-" + UUID.randomUUID(), operations).inChunksOf(1)
                .finishingAt(finishedPath));
    }

    /** The profile's modules and the modules they depend on, each after its dependencies. */
    public List<String> moduleOrder(InstallProfile profile) {
        Set<String> needed = new LinkedHashSet<>();
        profile.modules().forEach(module -> collect(module, needed));
        return modules.installOrder().stream().map(ModuleInfo::name).filter(needed::contains).toList();
    }

    private void collect(String module, Set<String> needed) {
        ModuleInfo info = modules.find(module).orElseThrow(() -> new IllegalStateException(
                "The profile names the module " + module + ", which the site does not have."));
        if (needed.add(module)) {
            info.dependencies().forEach(dependency -> collect(dependency, needed));
        }
    }
}
