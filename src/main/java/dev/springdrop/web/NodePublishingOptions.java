package dev.springdrop.web;

import dev.springdrop.kernel.datetime.DateFormatService;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.menu.MenuConfig;
import dev.springdrop.kernel.menu.MenuLink;
import dev.springdrop.kernel.menu.MenuManager;
import dev.springdrop.kernel.menu.NodeMenuLinks;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeFieldAccessRule;
import dev.springdrop.kernel.security.Permissions;
import dev.springdrop.kernel.user.AccountPrincipals;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.user.UserAccountService;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * The publishing options beside a node's fields: whether it is published, who
 * wrote it and when, whether it is promoted and sticky, and its menu link.
 * Someone who administers nodes sets the first four, and someone who
 * administers menus sets the link. For anyone else those controls are left
 * off the form and a submission naming them changes nothing. A node under a
 * workflow is published by moving it between states, so it has no published
 * box.
 */
@Component
public class NodePublishingOptions {

    public static final String STATUS = "status";

    public static final String AUTHOR = "author";

    public static final String CREATED = "created";

    public static final String PROMOTE = "promote";

    public static final String STICKY = "sticky";

    public static final String MENU = "menu_enabled";

    public static final String MENU_TITLE = "menu_title";

    public static final String MENU_NAME = "menu_name";

    public static final String MENU_WEIGHT = "menu_weight";

    /** What a datetime-local control sends: a date and a time to the minute, with optional seconds. */
    static final String DATE_TIME_PATTERN = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2})?";

    static final String WEIGHT_PATTERN = "-?\\d{1,4}";

    private static final DateTimeFormatter LOCAL_MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final UserAccountService accounts;
    private final NodeMenuLinks menuLinks;
    private final MenuManager menus;
    private final DateFormatService dates;

    public NodePublishingOptions(UserAccountService accounts, NodeMenuLinks menuLinks, MenuManager menus,
            DateFormatService dates) {
        this.accounts = accounts;
        this.menuLinks = menuLinks;
        this.menus = menus;
        this.dates = dates;
    }

    public static boolean administersNodes(Authentication who) {
        return NodeFieldAccessRule.administersNodes(who);
    }

    public static boolean administersMenus(Authentication who) {
        return AccountPrincipals.bypassesChecks(who) || holds(who, Permissions.ADMINISTER_MENU);
    }

    private static boolean holds(Authentication who, String permission) {
        return who.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals(permission));
    }

    /**
     * The controls the person may use, showing the node as it stands, or what
     * was typed for the author and date when the form comes back.
     */
    public List<FormElement> elements(EntityData node, boolean moderated, Map<String, String> submitted,
            Authentication who) {
        List<FormElement> elements = new ArrayList<>();
        if (administersNodes(who)) {
            if (!moderated) {
                elements.add(FormElement.of(ElementType.CHECKBOX, STATUS).label("Published")
                        .value(Boolean.TRUE.equals(node.fields().get(BaseFieldDefinition.STATUS))));
            }
            elements.add(FormElement.of(ElementType.DETAILS, "authoring-information").label("Authoring information")
                    .child(FormElement.of(ElementType.TEXTFIELD, AUTHOR).label("Authored by")
                            .description("The username of the author. Blank for anonymous.")
                            .value(submitted.getOrDefault(AUTHOR, authorName(node)))
                            .rule(ValidationRule.maxLength(60)))
                    .child(FormElement.of(ElementType.DATETIME, CREATED).label("Authored on")
                            .description("Blank for the time the content is first saved.")
                            .value(submitted.getOrDefault(CREATED, createdValue(node)))
                            .rule(ValidationRule.pattern(DATE_TIME_PATTERN)
                                    .withMessage("Enter a date and time."))));
            elements.add(FormElement.of(ElementType.DETAILS, "promotion-options").label("Promotion options")
                    .child(FormElement.of(ElementType.CHECKBOX, PROMOTE).label("Promoted to front page")
                            .value(Boolean.TRUE.equals(node.fields().get(NodeEntityType.PROMOTE))))
                    .child(FormElement.of(ElementType.CHECKBOX, STICKY).label("Sticky at top of lists")
                            .value(Boolean.TRUE.equals(node.fields().get(NodeEntityType.STICKY)))));
        }
        if (administersMenus(who)) {
            elements.add(menuSettings(node, submitted));
        }
        return elements;
    }

    private FormElement menuSettings(EntityData node, Map<String, String> submitted) {
        Optional<MenuLink> link = node.id() == null ? Optional.empty() : menuLinks.linkOf(node.id());
        boolean returned = submitted.containsKey(MENU_NAME);
        boolean enabled = returned ? FormRenderer.CHECKED_VALUE.equals(submitted.get(MENU)) : link.isPresent();
        return FormElement.of(ElementType.DETAILS, "menu-settings").label("Menu settings")
                .child(FormElement.of(ElementType.CHECKBOX, MENU).label("Provide a menu link").value(enabled))
                .child(FormElement.of(ElementType.TEXTFIELD, MENU_TITLE).label("Menu link title")
                        .value(returned ? submitted.getOrDefault(MENU_TITLE, "")
                                : link.map(MenuLink::title).orElse(node.label()))
                        .visibleWhen(MENU, FormRenderer.CHECKED_VALUE)
                        .requiredWhen(MENU, FormRenderer.CHECKED_VALUE)
                        .rule(ValidationRule.maxLength(255)))
                .child(FormElement.of(ElementType.SELECT, MENU_NAME).label("Menu")
                        .value(returned ? submitted.get(MENU_NAME) : link.map(MenuLink::menu).orElse(MenuConfig.MAIN))
                        .visibleWhen(MENU, FormRenderer.CHECKED_VALUE)
                        .options(offeredMenus().stream().map(menu -> new SelectOption(menu.id(), menu.label()))
                                .toList()))
                .child(FormElement.of(ElementType.NUMBER, MENU_WEIGHT).label("Weight")
                        .value(returned ? submitted.getOrDefault(MENU_WEIGHT, "0")
                                : String.valueOf(link.map(MenuLink::weight).orElse(0)))
                        .visibleWhen(MENU, FormRenderer.CHECKED_VALUE)
                        .rule(ValidationRule.pattern(WEIGHT_PATTERN).withMessage("The weight is a whole number.")));
    }

    /**
     * Sets the options the person may set on the node's fields, and gives what
     * is wrong with the submission that the form's own rules cannot see.
     */
    public Map<String, String> apply(Map<String, Object> fields, Map<String, String> submitted, boolean moderated,
            Authentication who) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (administersNodes(who)) {
            if (!moderated) {
                fields.put(BaseFieldDefinition.STATUS, ticked(submitted, STATUS));
            }
            fields.put(NodeEntityType.PROMOTE, ticked(submitted, PROMOTE));
            fields.put(NodeEntityType.STICKY, ticked(submitted, STICKY));
            String author = submitted.getOrDefault(AUTHOR, "").strip();
            if (author.isEmpty()) {
                fields.put(BaseFieldDefinition.OWNER, UserAccount.ANONYMOUS_ID);
            } else {
                accounts.findByName(author).ifPresentOrElse(
                        account -> fields.put(BaseFieldDefinition.OWNER, account.id()),
                        () -> errors.put(AUTHOR, "There is no account named " + author + "."));
            }
            String created = submitted.getOrDefault(CREATED, "");
            if (created.matches(DATE_TIME_PATTERN)) {
                fields.put(BaseFieldDefinition.CREATED, LocalDateTime.parse(created)
                        .atZone(dates.siteTimezone()).toOffsetDateTime());
            }
        }
        if (administersMenus(who) && ticked(submitted, MENU) && offeredMenus().stream()
                .noneMatch(menu -> menu.id().equals(submitted.get(MENU_NAME)))) {
            errors.put(MENU_NAME, "Choose a menu.");
        }
        return errors;
    }

    /** Gives the saved node the menu link the form asked for, or removes the one it had. */
    public void saveMenuLink(EntityData saved, Map<String, String> submitted, Authentication who) {
        if (!administersMenus(who)) {
            return;
        }
        if (ticked(submitted, MENU)) {
            String weight = submitted.getOrDefault(MENU_WEIGHT, "0");
            menuLinks.save(saved.id(), submitted.get(MENU_NAME), submitted.get(MENU_TITLE).strip(),
                    weight.matches(WEIGHT_PATTERN) ? Integer.parseInt(weight) : 0);
        } else {
            menuLinks.remove(saved.id());
        }
    }

    /** The menus content may hang in: every one but the administration and account menus. */
    private List<MenuConfig> offeredMenus() {
        return menus.all().stream()
                .filter(menu -> !menu.id().equals(MenuConfig.ADMIN) && !menu.id().equals(MenuConfig.ACCOUNT))
                .toList();
    }

    private String authorName(EntityData node) {
        Object owner = node.fields().get(BaseFieldDefinition.OWNER);
        return owner instanceof Number id ? accounts.find(id.longValue()).map(UserAccount::name)
                .filter(name -> id.longValue() != UserAccount.ANONYMOUS_ID).orElse("") : "";
    }

    private String createdValue(EntityData node) {
        return node.fields().get(BaseFieldDefinition.CREATED) instanceof OffsetDateTime created
                ? LOCAL_MINUTES.format(created.atZoneSameInstant(dates.siteTimezone())) : "";
    }

    private static boolean ticked(Map<String, String> submitted, String name) {
        return FormRenderer.CHECKED_VALUE.equals(submitted.get(name));
    }
}
