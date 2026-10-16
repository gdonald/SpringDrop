package dev.springdrop.web;

import dev.springdrop.kernel.datetime.DateFormatService;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.user.ApiTokenService;
import dev.springdrop.kernel.user.UserAccount;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * The signed-in account's personal access tokens: the list, creating one under
 * a label, and revoking one. A new token is shown once, on the page that
 * creates it.
 */
@Controller
public class ApiTokenController {

    public static final String PATH = "/user/tokens";

    public static final String LABEL = "label";

    private static final String DATE_FORMAT = "short";

    private final ApiTokenService tokens;
    private final FormBuilder formBuilder;
    private final FormRenderer renderer;
    private final DateFormatService dates;

    public ApiTokenController(ApiTokenService tokens, FormBuilder formBuilder, FormRenderer renderer,
            DateFormatService dates) {
        this.tokens = tokens;
        this.formBuilder = formBuilder;
        this.renderer = renderer;
        this.dates = dates;
    }

    /** One token's row: its label and when it was made and last used, in the site's short date format. */
    public record Row(long id, String label, String created, String lastUsed) {
    }

    public static String revokePath(long tokenId) {
        return PATH + "/" + tokenId + "/revoke";
    }

    @GetMapping(PATH)
    public String list(Model model) {
        return page(tokenForm(""), Map.of(), null, model);
    }

    @PostMapping(PATH)
    public String create(@RequestParam Map<String, String> submitted, Model model) {
        long account = signedIn();
        String label = submitted.getOrDefault(LABEL, "");
        FormElement tree = tokenForm(label);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        if (state.hasErrors()) {
            return page(tree, state.errors(), null, model);
        }
        return page(tokenForm(""), Map.of(), tokens.issue(account, label).secret(), model);
    }

    @PostMapping(PATH + "/{tokenId}/revoke")
    public String revoke(@PathVariable long tokenId) {
        tokens.revoke(signedIn(), tokenId);
        return "redirect:" + PATH;
    }

    private static long signedIn() {
        long account = CurrentAccount.id();
        if (account == UserAccount.ANONYMOUS_ID) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Sign in to manage API tokens.");
        }
        return account;
    }

    private static FormElement tokenForm(String label) {
        return FormElement.of(ElementType.CONTAINER, "api-token")
                .child(FormElement.of(ElementType.TEXTFIELD, LABEL).label("Label")
                        .description("What the token is for, such as the program that sends it.")
                        .value(label)
                        .markRequired()
                        .rule(ValidationRule.maxLength(ApiTokenService.LABEL_MAX_LENGTH)))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "create").label("Create token")));
    }

    private String page(FormElement tree, Map<String, String> errors, String issued, Model model) {
        model.addAttribute("title", "API tokens");
        model.addAttribute("action", PATH);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        model.addAttribute("issued", issued);
        model.addAttribute("tokens", tokens.tokensOf(signedIn()).stream().map(token -> new Row(token.id(),
                token.label(), dates.format(token.created().toInstant(), DATE_FORMAT, null),
                token.lastUsed() == null ? "Never" : dates.format(token.lastUsed().toInstant(), DATE_FORMAT, null)))
                .toList());
        return "user/api-tokens";
    }
}
