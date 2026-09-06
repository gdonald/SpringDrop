package dev.springdrop.web;

import dev.springdrop.kernel.permission.PermissionDefinition;
import dev.springdrop.kernel.permission.PermissionRegistry;
import dev.springdrop.kernel.role.RoleConfig;
import dev.springdrop.kernel.role.RoleManager;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;

/**
 * Who may do what, as a table of permissions against roles. A permission the
 * site warns about is marked, so nobody hands one out without seeing that it
 * reaches past the site's own rules.
 */
@Controller
public class RolePermissionsController {

    public static final String PATH = "/admin/people/permissions";

    /** How a checkbox in the table is named: the role and the permission it grants. */
    public static final String SEPARATOR = "|";

    private final RoleManager roles;
    private final PermissionRegistry permissions;

    public RolePermissionsController(RoleManager roles, PermissionRegistry permissions) {
        this.roles = roles;
        this.permissions = permissions;
    }

    @GetMapping(PATH)
    public String show(Model model) {
        model.addAttribute("title", "Permissions");
        model.addAttribute("description", "What each role may do.");
        model.addAttribute("action", PATH);
        model.addAttribute("listing", matrix());
        model.addAttribute("formMarkup", "");
        return "admin/permissions";
    }

    @PostMapping(PATH)
    public String save(@RequestParam Map<String, String> submitted) {
        for (RoleConfig role : roles.all()) {
            RoleConfig updated = role;
            for (PermissionDefinition permission : permissions.all()) {
                updated = submitted.containsKey(checkboxName(role, permission))
                        ? updated.granting(permission.name())
                        : updated.revoking(permission.name());
            }
            roles.save(updated);
        }
        return "redirect:" + PATH;
    }

    public static String checkboxName(RoleConfig role, PermissionDefinition permission) {
        return role.id() + SEPARATOR + permission.name();
    }

    private String matrix() {
        List<RoleConfig> allRoles = roles.all();

        StringBuilder markup = new StringBuilder("<table class=\"table align-middle\"><thead><tr>")
                .append("<th scope=\"col\">Permission</th>");
        for (RoleConfig role : allRoles) {
            markup.append("<th scope=\"col\">").append(HtmlUtils.htmlEscape(role.label())).append("</th>");
        }
        markup.append("</tr></thead><tbody>");

        for (PermissionDefinition permission : permissions.all()) {
            markup.append("<tr><td>")
                    .append(HtmlUtils.htmlEscape(permission.title()));
            if (permission.restricted()) {
                markup.append(" <span class=\"badge text-bg-warning\">Trusted roles only</span>");
            }
            markup.append("<div class=\"form-text\">")
                    .append(HtmlUtils.htmlEscape(permission.description()))
                    .append("</div></td>");

            for (RoleConfig role : allRoles) {
                markup.append("<td><input type=\"checkbox\" class=\"form-check-input\" name=\"")
                        .append(HtmlUtils.htmlEscape(checkboxName(role, permission))).append("\"")
                        .append(role.grants(permission.name()) ? " checked" : "")
                        .append(role.administrator() ? " disabled" : "")
                        .append("></td>");
            }
            markup.append("</tr>");
        }
        return markup.append("</tbody></table>").toString();
    }
}
