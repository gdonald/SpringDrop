package dev.springdrop.web;

import dev.springdrop.kernel.theme.Messenger;
import dev.springdrop.kernel.theme.StatusMessage;
import dev.springdrop.kernel.update.UpdateManager;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * The update page: the routines core and the modules have yet to run, and a
 * button running them with the site in maintenance mode meanwhile.
 */
@Controller
public class UpdateController {

    public static final String PATH = "/update";

    private final UpdateManager updates;
    private final Messenger messenger;

    public UpdateController(UpdateManager updates, Messenger messenger) {
        this.updates = updates;
        this.messenger = messenger;
    }

    @GetMapping(PATH)
    public String list(Model model) {
        model.addAttribute("title", "Database updates");
        model.addAttribute("pending", updates.pending());
        model.addAttribute("action", PATH);
        return "admin/updates";
    }

    @PostMapping(PATH)
    public String run() {
        UpdateManager.Report report = updates.runPending();
        messenger.success("Applied " + report.applied().size() + " update"
                + (report.applied().size() == 1 ? "." : "s."));
        report.failed().ifPresent(routine -> messenger.add(StatusMessage.error("The " + routine.module()
                + " update " + routine.version() + " failed: " + report.reason())));
        return "redirect:" + PATH;
    }
}
