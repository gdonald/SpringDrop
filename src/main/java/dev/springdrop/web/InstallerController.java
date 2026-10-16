package dev.springdrop.web;

import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.install.InstallChoices;
import dev.springdrop.kernel.install.InstallProfile;
import dev.springdrop.kernel.install.InstallRequirements;
import dev.springdrop.kernel.install.Installer;
import dev.springdrop.kernel.install.InstallerFilter;
import dev.springdrop.kernel.language.LanguageManager;
import dev.springdrop.kernel.security.SecurityConfig;
import jakarta.servlet.http.HttpSession;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The installer, a step at a time: the site's language, a check of what the
 * site needs, the profile, and the site's details with the administrator's
 * account. The last step starts the install batch, which ends at the sign-in
 * page. A step reached before the ones it needs sends the person back to the
 * first of those.
 */
@Controller
public class InstallerController {

    public static final String PATH = InstallerFilter.INSTALL_PATH;

    public static final String REQUIREMENTS_PATH = PATH + "/requirements";

    public static final String PROFILE_PATH = PATH + "/profile";

    public static final String SITE_PATH = PATH + "/site";

    public static final String LANGCODE = "langcode";

    public static final String PROFILE = "profile";

    public static final String SITE_NAME = "site_name";

    public static final String SITE_MAIL = "site_mail";

    public static final String ADMIN_NAME = "admin_name";

    public static final String ADMIN_MAIL = "admin_mail";

    public static final String PASSWORD = "password";

    static final String CHOICES = InstallChoices.class.getName();

    private static final List<String> STEPS = List.of("Choose language", "Verify requirements", "Choose profile",
            "Configure site");

    private final Installer installer;
    private final InstallRequirements requirements;
    private final PasswordEncoder passwords;
    private final FormBuilder formBuilder;
    private final FormRenderer renderer;

    public InstallerController(Installer installer, InstallRequirements requirements, PasswordEncoder passwords,
            FormBuilder formBuilder, FormRenderer renderer) {
        this.installer = installer;
        this.requirements = requirements;
        this.passwords = passwords;
        this.formBuilder = formBuilder;
        this.renderer = renderer;
    }

    @GetMapping(PATH)
    public String language(HttpSession session, Model model) {
        String chosen = choices(session).langcode();
        return form(0, PATH, languageForm(chosen == null ? LanguageManager.ENGLISH : chosen), Map.of(), model);
    }

    @PostMapping(PATH)
    public String chooseLanguage(@RequestParam Map<String, String> submitted, HttpSession session, Model model) {
        String langcode = submitted.getOrDefault(LANGCODE, "");
        FormElement tree = languageForm(langcode);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        if (!Arrays.asList(Locale.getISOLanguages()).contains(langcode)) {
            state.error(LANGCODE, "Choose a language.");
        }
        if (state.hasErrors()) {
            return form(0, PATH, tree, state.errors(), model);
        }
        session.setAttribute(CHOICES, choices(session).withLangcode(langcode));
        return "redirect:" + REQUIREMENTS_PATH;
    }

    @GetMapping(REQUIREMENTS_PATH)
    public String requirements(HttpSession session, Model model) {
        if (choices(session).langcode() == null) {
            return "redirect:" + PATH;
        }
        page(1, model);
        model.addAttribute("requirements", requirements.check());
        model.addAttribute("met", requirements.allMet());
        model.addAttribute("next", PROFILE_PATH);
        model.addAttribute("again", REQUIREMENTS_PATH);
        return "install/requirements";
    }

    @GetMapping(PROFILE_PATH)
    public String profile(HttpSession session, Model model) {
        Optional<String> earlier = earlierStep(session, false);
        if (earlier.isPresent()) {
            return earlier.get();
        }
        String chosen = choices(session).profile();
        return form(2, PROFILE_PATH, profileForm(chosen == null ? installer.profiles().getFirst().id() : chosen),
                Map.of(), model);
    }

    @PostMapping(PROFILE_PATH)
    public String chooseProfile(@RequestParam Map<String, String> submitted, HttpSession session, Model model) {
        Optional<String> earlier = earlierStep(session, false);
        if (earlier.isPresent()) {
            return earlier.get();
        }
        String profile = submitted.getOrDefault(PROFILE, "");
        FormElement tree = profileForm(profile);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        if (installer.profile(profile).isEmpty()) {
            state.error(PROFILE, "Choose a profile.");
        }
        if (state.hasErrors()) {
            return form(2, PROFILE_PATH, tree, state.errors(), model);
        }
        session.setAttribute(CHOICES, choices(session).withProfile(profile));
        return "redirect:" + SITE_PATH;
    }

    @GetMapping(SITE_PATH)
    public String site(HttpSession session, Model model) {
        Optional<String> earlier = earlierStep(session, true);
        if (earlier.isPresent()) {
            return earlier.get();
        }
        return form(3, SITE_PATH, siteForm(Map.of()), Map.of(), model);
    }

    @PostMapping(SITE_PATH)
    public String install(@RequestParam Map<String, String> submitted, HttpSession session, Model model) {
        Optional<String> earlier = earlierStep(session, true);
        if (earlier.isPresent()) {
            return earlier.get();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String name : List.of(SITE_NAME, SITE_MAIL, ADMIN_NAME, ADMIN_MAIL)) {
            values.put(name, submitted.getOrDefault(name, "").strip());
        }
        values.put(PASSWORD, submitted.getOrDefault(PASSWORD, ""));
        FormElement tree = siteForm(values);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(values));
        if (state.hasErrors()) {
            return form(3, SITE_PATH, tree, state.errors(), model);
        }
        InstallChoices choices = choices(session);
        String batch = installer.start(installer.profile(choices.profile()).orElseThrow(),
                new Installer.SiteDetails(choices.langcode(), values.get(SITE_NAME), values.get(SITE_MAIL),
                        values.get(ADMIN_NAME), values.get(ADMIN_MAIL), passwords.encode(values.get(PASSWORD))),
                SecurityConfig.LOGIN_PATH).batchId();
        session.removeAttribute(CHOICES);
        return "redirect:" + BatchController.pathFor(batch);
    }

    private static InstallChoices choices(HttpSession session) {
        return session.getAttribute(CHOICES) instanceof InstallChoices choices ? choices : InstallChoices.NONE;
    }

    /** The first earlier step the person has not finished, as a redirect, or nothing when they all are. */
    private Optional<String> earlierStep(HttpSession session, boolean needsProfile) {
        InstallChoices choices = choices(session);
        if (choices.langcode() == null) {
            return Optional.of("redirect:" + PATH);
        }
        if (!requirements.allMet()) {
            return Optional.of("redirect:" + REQUIREMENTS_PATH);
        }
        if (needsProfile && choices.profile() == null) {
            return Optional.of("redirect:" + PROFILE_PATH);
        }
        return Optional.empty();
    }

    private static FormElement languageForm(String langcode) {
        return FormElement.of(ElementType.CONTAINER, "install-language")
                .child(FormElement.of(ElementType.SELECT, LANGCODE).label("Language")
                        .description("The language the site is built in. More can be added later.")
                        .value(langcode).markRequired()
                        .options(Arrays.stream(Locale.getISOLanguages())
                                .map(code -> new SelectOption(code, Locale.of(code).getDisplayLanguage(Locale.ENGLISH)))
                                .sorted((left, right) -> left.label().compareTo(right.label())).toList()))
                .child(actions("Save and continue"));
    }

    private FormElement profileForm(String profile) {
        return FormElement.of(ElementType.CONTAINER, "install-profile")
                .child(FormElement.of(ElementType.RADIOS, PROFILE).label("Profile")
                        .value(profile).markRequired()
                        .options(installer.profiles().stream().map(each -> new SelectOption(each.id(),
                                each.label() + ": " + each.description())).toList()))
                .child(actions("Save and continue"));
    }

    private static FormElement siteForm(Map<String, String> values) {
        return FormElement.of(ElementType.CONTAINER, "install-site")
                .child(FormElement.of(ElementType.FIELDSET, "site").label("Site information")
                        .child(FormElement.of(ElementType.TEXTFIELD, SITE_NAME).label("Site name")
                                .value(values.get(SITE_NAME)).markRequired().rule(ValidationRule.maxLength(128)))
                        .child(FormElement.of(ElementType.TEXTFIELD, SITE_MAIL).label("Site email address")
                                .description("The address the site's mail is sent from.")
                                .value(values.get(SITE_MAIL)).markRequired().rule(ValidationRule.maxLength(254))
                                .rule(ValidationRule.email())))
                .child(FormElement.of(ElementType.FIELDSET, "administrator").label("Site maintenance account")
                        .child(FormElement.of(ElementType.TEXTFIELD, ADMIN_NAME).label("Username")
                                .value(values.get(ADMIN_NAME)).markRequired().rule(ValidationRule.maxLength(60)))
                        .child(FormElement.of(ElementType.TEXTFIELD, ADMIN_MAIL).label("Email address")
                                .value(values.get(ADMIN_MAIL)).markRequired().rule(ValidationRule.maxLength(254))
                                .rule(ValidationRule.email()))
                        .child(FormElement.of(ElementType.PASSWORD, PASSWORD).label("Password")
                                .markRequired().rule(ValidationRule.pattern(".{8,}")
                                        .withMessage("Use at least 8 characters."))))
                .child(actions("Install SpringDrop"));
    }

    private static FormElement actions(String label) {
        return FormElement.of(ElementType.ACTIONS, "actions").child(FormElement.of(ElementType.SUBMIT, "next")
                .label(label));
    }

    private String form(int step, String action, FormElement tree, Map<String, String> errors, Model model) {
        page(step, model);
        model.addAttribute("action", action);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "install/step";
    }

    private static void page(int step, Model model) {
        model.addAttribute("title", STEPS.get(step));
        model.addAttribute("steps", STEPS);
        model.addAttribute("step", step);
    }
}
