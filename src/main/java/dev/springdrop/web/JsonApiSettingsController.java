package dev.springdrop.web;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.jsonapi.JsonApiSettings;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Whether JSON:API accepts writes or only reads. */
@Controller
public class JsonApiSettingsController {

    public static final String PATH = "/admin/config/services/jsonapi";

    public static final String OPERATIONS = "operations";

    public static final String READ_ONLY = "read_only";

    public static final String READ_WRITE = "read_write";

    private final ConfigStore config;

    public JsonApiSettingsController(ConfigStore config) {
        this.config = config;
    }

    @GetMapping(PATH)
    public String form(Model model) {
        model.addAttribute("title", "JSON:API");
        model.addAttribute("readOnly",
                config.read(JsonApiSettings.CONFIG_NAME, JsonApiSettings.class, JsonApiSettings.DEFAULTS).readOnly());
        model.addAttribute("action", PATH);
        return "admin/jsonapi-settings";
    }

    @PostMapping(PATH)
    public String save(@RequestParam(name = OPERATIONS, defaultValue = READ_ONLY) String operations) {
        config.save(JsonApiSettings.CONFIG_NAME, new JsonApiSettings(!operations.equals(READ_WRITE)));
        return "redirect:" + PATH;
    }
}
