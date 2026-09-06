package dev.springdrop.web;

import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.entity.query.EntityQuery;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.entity.query.Sort;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.role.RoleConfig;
import dev.springdrop.kernel.role.RoleManager;
import dev.springdrop.kernel.user.AccountCancellationService;
import dev.springdrop.kernel.user.CancellationMethod;
import dev.springdrop.kernel.user.UserAccount;
import dev.springdrop.kernel.user.UserAccountService;
import dev.springdrop.kernel.user.UserEntityType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;

/**
 * The people on the site: who they are, what they may do, and closing an account
 * when someone leaves. Closing asks first, and says plainly what will become of
 * anything the person wrote.
 */
@Controller
public class PeopleController {

    public static final String PATH = "/admin/people";

    public static final String NAME_FILTER = "name";

    public static final String STATUS_FILTER = "status";

    public static final String ROLE_FILTER = "role";

    public static final String METHOD = "method";

    private final UserAccountService accounts;
    private final RoleManager roles;
    private final AccountCancellationService cancellations;
    private final EntityCrudService entities;
    private final EntityQueryExecutor queries;
    private final FormRenderer renderer;

    public PeopleController(
            UserAccountService accounts,
            RoleManager roles,
            AccountCancellationService cancellations,
            EntityCrudService entities,
            EntityQueryExecutor queries,
            FormRenderer renderer) {
        this.accounts = accounts;
        this.roles = roles;
        this.cancellations = cancellations;
        this.entities = entities;
        this.queries = queries;
        this.renderer = renderer;
    }

    @GetMapping(PATH)
    public String list(
            @RequestParam(name = NAME_FILTER, defaultValue = "") String name,
            @RequestParam(name = STATUS_FILTER, defaultValue = "") String status,
            @RequestParam(name = ROLE_FILTER, defaultValue = "") String role,
            Model model) {

        model.addAttribute("title", "People");
        model.addAttribute("description", "The accounts on this site.");
        model.addAttribute("action", PATH);
        model.addAttribute("formMarkup", renderer.render(filterForm(name, status, role)));
        model.addAttribute("listing", listing(matching(name, status, role)));
        return "admin/people";
    }

    @GetMapping(PATH + "/{id}/edit")
    public String edit(@PathVariable long id, Model model) {
        EntityData account = entities.load(UserEntityType.ID, id).orElseThrow();

        model.addAttribute("title", "Edit " + account.label());
        model.addAttribute("description", "What this account may do.");
        model.addAttribute("action", PATH + "/" + id + "/edit");
        model.addAttribute("formMarkup", renderer.render(rolesForm(account)));
        model.addAttribute("listing", "");
        return "admin/people";
    }

    @PostMapping(PATH + "/{id}/edit")
    public String save(@PathVariable long id, @RequestParam Map<String, String> submitted) {
        entities.load(UserEntityType.ID, id).ifPresent(account -> {
            List<String> held = new ArrayList<>();
            roles.all().stream()
                    .filter(role -> !RoleConfig.ANONYMOUS.equals(role.id()))
                    .filter(role -> !RoleConfig.AUTHENTICATED.equals(role.id()))
                    .filter(role -> submitted.containsKey(roleCheckbox(role)))
                    .forEach(role -> held.add(role.id()));

            Map<String, Object> values = new LinkedHashMap<>(account.fields());
            values.put(UserEntityType.ROLES, held);
            entities.save(account.withFields(values));
        });
        return "redirect:" + PATH;
    }

    @GetMapping(PATH + "/{id}/cancel")
    public String confirmCancel(@PathVariable long id, Model model) {
        UserAccount account = accounts.find(id).orElseThrow();

        model.addAttribute("title", "Close the account " + account.name() + "?");
        model.addAttribute("description", "Choose what becomes of anything this account wrote.");
        model.addAttribute("action", PATH + "/" + id + "/cancel");
        model.addAttribute("formMarkup", renderer.render(cancelForm(id)));
        model.addAttribute("listing", "");
        return "admin/people";
    }

    @PostMapping(PATH + "/{id}/cancel")
    public String cancel(
            @PathVariable long id,
            @RequestParam(name = METHOD, defaultValue = "BLOCK") String method) {

        cancellations.cancel(id, CancellationMethod.valueOf(method));
        return "redirect:" + PATH;
    }

    public static String roleCheckbox(RoleConfig role) {
        return "role_" + role.id();
    }

    private List<UserAccount> matching(String name, String status, String role) {
        EntityQuery query = queries.query(UserEntityType.ID).sort(Sort.ascending("label"));
        if (!name.isBlank()) {
            query.condition(Condition.contains("label", name));
        }
        if (!status.isBlank()) {
            query.condition(Condition.equal("status", Boolean.parseBoolean(status)));
        }

        List<UserAccount> found = new ArrayList<>();
        for (Object id : query.ids()) {
            long accountId = ((Number) id).longValue();
            if (holdsRole(accountId, role)) {
                accounts.find(accountId).ifPresent(found::add);
            }
        }
        return found;
    }

    private boolean holdsRole(long accountId, String role) {
        if (role.isBlank()) {
            return true;
        }
        return entities.load(UserEntityType.ID, accountId)
                .map(account -> account.fields().get(UserEntityType.ROLES))
                .filter(List.class::isInstance)
                .map(held -> ((List<?>) held).contains(role))
                .orElse(false);
    }

    private String listing(List<UserAccount> found) {
        StringBuilder markup = new StringBuilder("<table class=\"table\"><thead><tr>"
                + "<th scope=\"col\">Name</th><th scope=\"col\">Email</th>"
                + "<th scope=\"col\">Status</th><th scope=\"col\">Actions</th></tr></thead><tbody>");

        for (UserAccount account : found) {
            String path = PATH + "/" + account.id();
            markup.append("<tr>")
                    .append(cell(account.name()))
                    .append(cell(account.mail()))
                    .append(cell(account.active() ? "Active" : "Blocked"))
                    .append("<td>")
                    .append("<a class=\"btn btn-secondary btn-sm\" href=\"")
                    .append(HtmlUtils.htmlEscape(path)).append("/edit\">Edit</a> ")
                    .append("<a class=\"btn btn-danger btn-sm\" href=\"")
                    .append(HtmlUtils.htmlEscape(path)).append("/cancel\">Close</a>")
                    .append("</td></tr>");
        }
        return markup.append("</tbody></table>").toString();
    }

    private static String cell(String text) {
        return "<td>" + HtmlUtils.htmlEscape(text) + "</td>";
    }

    private FormElement filterForm(String name, String status, String role) {
        List<SelectOption> roleOptions = new ArrayList<>();
        roleOptions.add(new SelectOption("", "Any role"));
        roles.all().forEach(each -> roleOptions.add(new SelectOption(each.id(), each.label())));

        return FormElement.of(ElementType.CONTAINER, "filters")
                .child(FormElement.of(ElementType.TEXTFIELD, NAME_FILTER).label("Name").value(name))
                .child(FormElement.of(ElementType.SELECT, STATUS_FILTER)
                        .label("Status")
                        .value(status)
                        .options(List.of(
                                new SelectOption("", "Any status"),
                                new SelectOption("true", "Active"),
                                new SelectOption("false", "Blocked"))))
                .child(FormElement.of(ElementType.SELECT, ROLE_FILTER)
                        .label("Role").value(role).options(roleOptions))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "filter").label("Filter")));
    }

    private FormElement rolesForm(EntityData account) {
        Object held = account.fields().get(UserEntityType.ROLES);
        List<?> heldRoles = (held instanceof List<?> names) ? names : List.of();

        FormElement form = FormElement.of(ElementType.CONTAINER, "roles");
        roles.all().stream()
                .filter(role -> !RoleConfig.ANONYMOUS.equals(role.id()))
                .filter(role -> !RoleConfig.AUTHENTICATED.equals(role.id()))
                .forEach(role -> form.child(FormElement.of(ElementType.CHECKBOX, roleCheckbox(role))
                        .label(role.label())
                        .value(heldRoles.contains(role.id()))));

        return form.child(FormElement.of(ElementType.ACTIONS, "actions")
                .child(FormElement.of(ElementType.SUBMIT, "save").label("Save roles")));
    }

    private FormElement cancelForm(long id) {
        List<SelectOption> options = new ArrayList<>();
        cancellations.methods().forEach(method ->
                options.add(new SelectOption(method.name(), method.label())));

        return FormElement.of(ElementType.CONTAINER, "cancel")
                .child(FormElement.of(ElementType.SELECT, METHOD)
                        .label("What to do")
                        .value(CancellationMethod.BLOCK.name())
                        .options(options))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "confirm").label("Close account"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(PATH)));
    }
}
