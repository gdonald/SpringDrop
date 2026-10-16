package dev.springdrop.kernel.install;

import dev.springdrop.kernel.batch.BatchOperationHandler;
import dev.springdrop.kernel.module.ModuleManager;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Enables one module of the profile being installed. */
@Component
public class InstallModuleOperation implements BatchOperationHandler {

    public static final String ID = "install.module";

    static final String MODULE = "module";

    private final ModuleManager modules;

    public InstallModuleOperation(ModuleManager modules) {
        this.modules = modules;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void run(Map<String, Object> payload) {
        modules.enable((String) payload.get(MODULE));
    }
}
