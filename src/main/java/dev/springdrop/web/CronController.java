package dev.springdrop.web;

import dev.springdrop.kernel.cron.CronRunner;
import dev.springdrop.kernel.datetime.DateFormatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Runs cron for an outside scheduler that knows the site's cron key, and shows
 * administrators when cron last ran, with a button to run it now.
 */
@Controller
public class CronController {

    public static final String TRIGGER_PATH = "/cron";

    public static final String PATH = "/admin/config/system/cron";

    public static final String RUN_PATH = PATH + "/run";

    private final CronRunner cron;
    private final DateFormatService dates;

    public CronController(CronRunner cron, DateFormatService dates) {
        this.cron = cron;
        this.dates = dates;
    }

    /** 204 once cron has run, 409 when a run already holds the lock, 403 for a wrong key. */
    @GetMapping(TRIGGER_PATH + "/{key}")
    public ResponseEntity<Void> trigger(@PathVariable String key) {
        if (!cron.isKey(key)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.status(cron.run() ? HttpStatus.NO_CONTENT : HttpStatus.CONFLICT).build();
    }

    @GetMapping(PATH)
    public String status(Model model) {
        model.addAttribute("title", "Cron");
        model.addAttribute("lastRun", cron.lastRun()
                .map(instant -> "Cron last ran " + dates.format(instant, "medium", null) + ".")
                .orElse("Cron has not run."));
        model.addAttribute("cronUrl", ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(TRIGGER_PATH + "/" + cron.key()).toUriString());
        model.addAttribute("jobs", cron.jobIds());
        model.addAttribute("runAction", RUN_PATH);
        return "admin/cron";
    }

    @PostMapping(RUN_PATH)
    public String run() {
        cron.run();
        return "redirect:" + PATH;
    }
}
