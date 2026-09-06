package dev.springdrop.web;

import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.user.AccountPrincipal;
import dev.springdrop.kernel.user.DuplicateAccountException;
import dev.springdrop.kernel.user.PasswordResetService;
import dev.springdrop.kernel.user.RegistrationService;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.user.UserSettings;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Signing up, asking for a way back in, and using the link that comes. A reset
 * link signs the person in once so they can set a new password, and is spent in
 * the process.
 */
@Controller
public class AccountController {

    public static final String REGISTER_PATH = "/user/register";

    public static final String PASSWORD_PATH = "/user/password";

    public static final String RESET_PATH = "/user/reset/{token}";

    public static final String VERIFY_PATH = "/user/verify/{token}";

    public static final String NAME = "name";

    public static final String MAIL = "mail";

    public static final String PASSWORD = "password";

    private final RegistrationService registrations;
    private final PasswordResetService resets;
    private final FormRenderer renderer;

    public AccountController(
            RegistrationService registrations, PasswordResetService resets, FormRenderer renderer) {
        this.registrations = registrations;
        this.resets = resets;
        this.renderer = renderer;
    }

    @GetMapping(REGISTER_PATH)
    public String showRegistration(Model model) {
        UserSettings settings = registrations.settings();
        model.addAttribute("title", "Create an account");
        model.addAttribute("action", REGISTER_PATH);
        model.addAttribute("message", settings.allowsSelfRegistration()
                ? approvalNotice(settings)
                : "Only an administrator creates accounts on this site.");
        model.addAttribute("formMarkup", settings.allowsSelfRegistration()
                ? renderer.render(registrationForm())
                : "");
        return "user/account";
    }

    @PostMapping(REGISTER_PATH)
    public String register(
            @RequestParam(NAME) String name,
            @RequestParam(MAIL) String mail,
            @RequestParam(PASSWORD) String password,
            Model model) {

        model.addAttribute("title", "Create an account");
        model.addAttribute("action", REGISTER_PATH);
        try {
            UserAccount account = registrations.register(name, mail, password);
            model.addAttribute("message", account.active()
                    ? "Your account is ready. You can sign in now."
                    : "Your account is waiting to be opened. We have sent you a message.");
            model.addAttribute("formMarkup", "");
        } catch (DuplicateAccountException clash) {
            model.addAttribute("message", "That name or address already belongs to an account.");
            model.addAttribute("formMarkup", renderer.render(registrationForm()));
        }
        return "user/account";
    }

    @GetMapping(VERIFY_PATH)
    public String verify(@PathVariable String token, Model model) {
        boolean verified = registrations.verifyMail(token);

        model.addAttribute("title", "Confirming your address");
        model.addAttribute("message", verified
                ? "Your address is confirmed. You can sign in now."
                : "That link has been used already, or it has lapsed.");
        model.addAttribute("formMarkup", "");
        return "user/account";
    }

    @GetMapping(PASSWORD_PATH)
    public String showPasswordRequest(Model model) {
        model.addAttribute("title", "Get back in");
        model.addAttribute("action", PASSWORD_PATH);
        model.addAttribute("message", "");
        model.addAttribute("formMarkup", renderer.render(passwordRequestForm()));
        return "user/account";
    }

    @PostMapping(PASSWORD_PATH)
    public String requestPassword(@RequestParam(NAME) String name, Model model) {
        resets.requestReset(name);

        model.addAttribute("title", "Get back in");
        // The same answer either way, so the form cannot be used to find out who
        // has an account here.
        model.addAttribute("message", "If that account exists, a link is on its way.");
        model.addAttribute("formMarkup", "");
        return "user/account";
    }

    @GetMapping(RESET_PATH)
    public String useResetLink(@PathVariable String token, HttpServletRequest request, Model model) {
        Optional<UserAccount> account = resets.claim(token);

        model.addAttribute("title", "Set a new password");
        model.addAttribute("action", RESET_PATH.replace("{token}", token));
        if (account.isEmpty()) {
            model.addAttribute("message", "That link has been used already, or it has lapsed.");
            model.addAttribute("formMarkup", "");
            return "user/account";
        }

        signIn(account.get(), request);
        model.addAttribute("message", "You are signed in. Set a new password to keep it that way.");
        model.addAttribute("formMarkup", renderer.render(newPasswordForm()));
        return "user/account";
    }

    @PostMapping(RESET_PATH)
    public String setPassword(
            @PathVariable String token,
            @RequestParam(PASSWORD) String password,
            Model model) {

        AccountPrincipal principal = signedInAccount();
        model.addAttribute("title", "Set a new password");
        model.addAttribute("formMarkup", "");
        if (principal == null) {
            model.addAttribute("message", "Sign in again before setting a password.");
            return "user/account";
        }

        resets.setPassword(principal.id(), password);
        model.addAttribute("message", "Your new password is saved.");
        return "user/account";
    }

    private static AccountPrincipal signedInAccount() {
        Object principal = Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(authentication -> authentication.getPrincipal())
                .orElse(null);
        return (principal instanceof AccountPrincipal account) ? account : null;
    }

    /** Signs the person in for this session, which is what a one-time link is for. */
    private static void signIn(UserAccount account, HttpServletRequest request) {
        AccountPrincipal principal =
                new AccountPrincipal(account.id(), account.name(), "", true, List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, "", principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    private static String approvalNotice(UserSettings settings) {
        if (settings.needsApproval()) {
            return "New accounts are opened by an administrator.";
        }
        return settings.requireEmailVerification()
                ? "We will send you a link to confirm your address."
                : "";
    }

    private static FormElement registrationForm() {
        return FormElement.of(ElementType.CONTAINER, "register")
                .child(FormElement.of(ElementType.TEXTFIELD, NAME)
                        .label("Username").markRequired().rule(ValidationRule.maxLength(60)))
                .child(FormElement.of(ElementType.TEXTFIELD, MAIL)
                        .label("Email address").markRequired().rule(ValidationRule.email()))
                .child(FormElement.of(ElementType.PASSWORD, PASSWORD)
                        .label("Password").markRequired().rule(ValidationRule.maxLength(128)))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "submit").label("Create account")));
    }

    private static FormElement passwordRequestForm() {
        return FormElement.of(ElementType.CONTAINER, "password-request")
                .child(FormElement.of(ElementType.TEXTFIELD, NAME).label("Username").markRequired())
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "submit").label("Send me a link")));
    }

    private static FormElement newPasswordForm() {
        return FormElement.of(ElementType.CONTAINER, "new-password")
                .child(FormElement.of(ElementType.PASSWORD, PASSWORD)
                        .label("New password").markRequired().rule(ValidationRule.maxLength(128)))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "submit").label("Save password")));
    }
}
