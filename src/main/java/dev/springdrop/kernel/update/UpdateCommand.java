package dev.springdrop.kernel.update;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Runs the pending update routines when the site starts with {@code --update}. */
@Component
public class UpdateCommand {

    public static final String OPTION = "update";

    private static final Logger LOG = LoggerFactory.getLogger(UpdateCommand.class);

    private final ApplicationArguments arguments;
    private final UpdateManager updates;

    public UpdateCommand(ApplicationArguments arguments, UpdateManager updates) {
        this.arguments = arguments;
        this.updates = updates;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(90)
    public void run() {
        if (arguments.containsOption(OPTION)) {
            UpdateManager.Report report = updates.runPending();
            LOG.info("Applied {} update routines.", report.applied().size());
            report.failed().ifPresent(routine -> LOG.error("The {} update {} failed: {}", routine.module(),
                    routine.version(), report.reason()));
        }
    }
}
