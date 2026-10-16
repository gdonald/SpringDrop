package dev.springdrop.kernel.install;

import dev.springdrop.kernel.batch.BatchOperationHandler;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Runs the install step of the profile being installed. */
@Component
public class InstallProfileOperation implements BatchOperationHandler {

    public static final String ID = "install.profile";

    static final String PROFILE = "profile";

    private final List<InstallProfile> profiles;

    public InstallProfileOperation(List<InstallProfile> profiles) {
        this.profiles = List.copyOf(profiles);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void run(Map<String, Object> payload) {
        profiles.stream().filter(profile -> profile.id().equals(payload.get(PROFILE))).findFirst().orElseThrow()
                .install();
    }
}
