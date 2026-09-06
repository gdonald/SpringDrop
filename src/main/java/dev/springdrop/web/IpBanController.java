package dev.springdrop.web;

import dev.springdrop.kernel.ban.IpBanService;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.ValidationRule;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The ban list: which addresses the site refuses, adding one, and letting one
 * back in.
 */
@Controller
public class IpBanController {

    public static final String PATH = "/admin/config/people/ban";

    public static final String UNBAN_PATH = PATH + "/unban";

    public static final String ADDRESS = "address";

    private final IpBanService bans;
    private final FormRenderer renderer;

    public IpBanController(IpBanService bans, FormRenderer renderer) {
        this.bans = bans;
        this.renderer = renderer;
    }

    @GetMapping(PATH)
    public String list(Model model) {
        model.addAttribute("title", "Banned addresses");
        model.addAttribute("description", "Requests from these addresses are refused before routing.");
        model.addAttribute("action", PATH);
        model.addAttribute("unbanAction", UNBAN_PATH);
        model.addAttribute("addressField", ADDRESS);
        model.addAttribute("formMarkup", renderer.render(banForm()));
        model.addAttribute("banned", bans.banned());
        return "admin/ban";
    }

    @PostMapping(PATH)
    public String ban(@RequestParam(name = ADDRESS, defaultValue = "") String address) {
        bans.ban(address);
        return "redirect:" + PATH;
    }

    @PostMapping(UNBAN_PATH)
    public String unban(@RequestParam(name = ADDRESS, defaultValue = "") String address) {
        bans.unban(address);
        return "redirect:" + PATH;
    }

    private static FormElement banForm() {
        return FormElement.of(ElementType.CONTAINER, "ban")
                .child(FormElement.of(ElementType.TEXTFIELD, ADDRESS)
                        .label("IP address")
                        .markRequired()
                        .rule(ValidationRule.maxLength(45)))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "add").label("Ban address")));
    }
}
