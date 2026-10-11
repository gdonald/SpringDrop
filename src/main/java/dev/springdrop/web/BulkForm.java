package dev.springdrop.web;

import dev.springdrop.kernel.action.ActionConfig;
import dev.springdrop.kernel.asset.Assets;
import dev.springdrop.kernel.security.SecurityHeadersFilter;
import dev.springdrop.kernel.views.handlers.BulkFormField;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * The form a listing's ticked results are sent with: the action to apply and
 * the apply button, with the form token and where to return. It is drawn per
 * request outside the cached listing, which holds only the boxes, and its
 * script keeps the button disabled until a box is ticked.
 */
@Component
public class BulkForm {

    public static final String ACTION = "action";

    public static final String DESTINATION = "destination";

    public static final String CONFIRMED = "confirmed";

    public static final String CHOOSE_ACTION = "Choose an action.";

    public static final String SELECT_ITEMS = "Select one or more items.";

    private final Assets assets;

    public BulkForm(Assets assets) {
        this.assets = assets;
    }

    public static String path(String viewId, String displayId) {
        return BulkOperationsController.PATH.replace("{view}", viewId).replace("{display}", displayId);
    }

    public String render(String viewId, String displayId, List<ActionConfig> actions, String destination,
            HttpServletRequest request) {
        StringBuilder markup = new StringBuilder("<form id=\"" + BulkFormField.FORM_ID + "\" class=\"views-bulk-form "
                + "row g-2 align-items-end mb-3\" method=\"post\" action=\""
                + HtmlUtils.htmlEscape(path(viewId, displayId)) + "\" data-bulk-select>");
        if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) {
            markup.append(hidden(token.getParameterName(), token.getToken()));
        }
        markup.append(hidden(DESTINATION, destination))
                .append("<div class=\"col-auto\"><label class=\"form-label\" for=\"views-bulk-action\">Action</label>")
                .append("<select class=\"form-select\" id=\"views-bulk-action\" name=\"").append(ACTION)
                .append("\" required><option value=\"\">- Choose an action -</option>");
        actions.forEach(action -> markup.append("<option value=\"").append(HtmlUtils.htmlEscape(action.id()))
                .append("\">").append(HtmlUtils.htmlEscape(action.label())).append("</option>"));
        markup.append("</select></div><div class=\"col-auto\"><button type=\"submit\" class=\"btn btn-primary\" "
                + "data-bulk-apply>Apply to selected items</button></div></form>");
        return markup.append("<script type=\"module\" nonce=\"")
                .append(HtmlUtils.htmlEscape(String.valueOf(request.getAttribute(SecurityHeadersFilter.NONCE_ATTRIBUTE))))
                .append("\">import { initBulkSelection } from '")
                .append(HtmlUtils.htmlEscape(assets.url("/js/bulk-selection.js")))
                .append("'; initBulkSelection();</script>").toString();
    }

    private static String hidden(String name, String value) {
        return "<input type=\"hidden\" name=\"" + HtmlUtils.htmlEscape(name) + "\" value=\""
                + HtmlUtils.htmlEscape(value) + "\">";
    }
}
