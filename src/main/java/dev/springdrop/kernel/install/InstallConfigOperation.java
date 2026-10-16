package dev.springdrop.kernel.install;

import dev.springdrop.kernel.batch.BatchOperationHandler;
import dev.springdrop.kernel.config.sync.ConfigSync;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Imports one config object of the profile being installed, checked against
 * its schema and dependencies like any single import. One that is refused
 * fails the operation with the reasons.
 */
@Component
public class InstallConfigOperation implements BatchOperationHandler {

    public static final String ID = "install.config";

    static final String PROFILE = "profile";

    static final String NAME = "name";

    private final List<InstallProfile> profiles;
    private final ProfileConfig profileConfig;
    private final ConfigSync sync;

    public InstallConfigOperation(List<InstallProfile> profiles, ProfileConfig profileConfig, ConfigSync sync) {
        this.profiles = List.copyOf(profiles);
        this.profileConfig = profileConfig;
        this.sync = sync;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void run(Map<String, Object> payload) {
        InstallProfile profile = profiles.stream().filter(each -> each.id().equals(payload.get(PROFILE))).findFirst()
                .orElseThrow();
        String name = (String) payload.get(NAME);
        List<String> problems = sync.importSingle(name, profileConfig.objects(profile).get(name));
        if (!problems.isEmpty()) {
            throw new IllegalStateException(String.join(" ", problems));
        }
    }
}
