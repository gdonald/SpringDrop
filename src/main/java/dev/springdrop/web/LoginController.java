package dev.springdrop.web;

import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.security.DestinationSuccessHandler;
import dev.springdrop.kernel.security.SecurityConfig;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The sign-in page. The form posts to the path the security layer watches, so
 * the credentials are checked before any controller sees them.
 */
@Controller
public class LoginController {

    public static final String USERNAME = "username";

    public static final String PASSWORD = "password";

    public static final String REMEMBER_ME = "remember-me";

    private final FormRenderer renderer;

    public LoginController(FormRenderer renderer) {
        this.renderer = renderer;
    }

    @GetMapping(SecurityConfig.LOGIN_PATH)
    public String show(
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "logout", required = false) String loggedOut,
            @RequestParam(name = DestinationSuccessHandler.DESTINATION, defaultValue = "") String destination,
            Model model) {

        model.addAttribute("action", SecurityConfig.LOGIN_PATH);
        model.addAttribute("message", message(error, loggedOut));
        model.addAttribute("formMarkup", renderer.render(loginForm(destination)));
        return "user/login";
    }

    private static String message(String error, String loggedOut) {
        if (error != null) {
            return "That name and password do not match an account that can sign in.";
        }
        return (loggedOut == null) ? "" : "You have signed out.";
    }

    private static FormElement loginForm(String destination) {
        return FormElement.of(ElementType.CONTAINER, "login")
                .child(FormElement.of(ElementType.HIDDEN, DestinationSuccessHandler.DESTINATION)
                        .value(destination))
                .child(FormElement.of(ElementType.TEXTFIELD, USERNAME)
                        .label("Username")
                        .markRequired()
                        .rule(ValidationRule.maxLength(60)))
                .child(FormElement.of(ElementType.PASSWORD, PASSWORD)
                        .label("Password")
                        .markRequired())
                .child(FormElement.of(ElementType.CHECKBOX, REMEMBER_ME).label("Keep me signed in"))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "submit").label("Sign in")));
    }
}
