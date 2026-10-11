package dev.springdrop.web;

import dev.springdrop.kernel.action.ActionConfig;
import dev.springdrop.kernel.action.BulkOperations;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.security.RedirectSafety;
import dev.springdrop.kernel.views.ViewConfig;
import dev.springdrop.kernel.views.ViewDisplay;
import dev.springdrop.kernel.views.ViewExecutor;
import dev.springdrop.kernel.views.ViewManager;
import dev.springdrop.kernel.views.ViewPaths;
import dev.springdrop.kernel.views.handlers.BulkFormField;
import dev.springdrop.kernel.web.EntityNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Applies the action chosen in a listing's bulk form to the results ticked.
 * The action must be one the listing offers and each item one of its type.
 * An action that cannot be undone shows the items and asks for confirmation
 * first. The work then runs as a batch, which returns to the listing.
 */
@Controller
public class BulkOperationsController {

    public static final String PATH = "/views/bulk/{view}/{display}";

    private final ViewManager views;
    private final ViewExecutor executor;
    private final BulkOperations bulk;
    private final RedirectSafety redirects;

    public BulkOperationsController(ViewManager views, ViewExecutor executor, BulkOperations bulk,
            RedirectSafety redirects) {
        this.views = views;
        this.executor = executor;
        this.bulk = bulk;
        this.redirects = redirects;
    }

    @PostMapping(PATH)
    public String apply(@PathVariable String view, @PathVariable String display,
            @RequestParam(name = BulkForm.ACTION, defaultValue = "") String actionId,
            @RequestParam(name = BulkFormField.ITEMS, required = false) List<String> submittedItems,
            @RequestParam(name = BulkForm.DESTINATION, defaultValue = "") String destination,
            @RequestParam(name = BulkForm.CONFIRMED, defaultValue = "") String confirmed,
            Authentication authentication, HttpServletResponse response, Model model) {
        ViewConfig config = views.find(view).orElseThrow(() -> new EntityNotFoundException("view", view));
        ViewDisplay shown = config.display(display)
                .orElseThrow(() -> new EntityNotFoundException("view display", display));
        List<ActionConfig> offered = bulk.offered(config, display);
        if (offered.isEmpty()) {
            throw new EntityNotFoundException("bulk form", view + "/" + display);
        }
        if (!executor.mayAccess(config, display, authentication)) {
            throw new AccessDeniedException("You may not see this listing.");
        }
        String returnTo = destination.startsWith("/") && redirects.isSafe(destination) ? destination
                : ViewPaths.address(shown);

        List<String> errors = new ArrayList<>();
        Optional<ActionConfig> action = offered.stream().filter(each -> each.id().equals(actionId)).findFirst();
        if (action.isEmpty()) {
            errors.add(BulkForm.CHOOSE_ACTION);
        }
        List<String> values = submittedItems == null ? List.of() : submittedItems;
        List<BulkOperations.Item> items = values.stream().flatMap(value -> BulkOperations.Item.parse(value).stream())
                .filter(item -> item.entityType().equals(config.baseEntityType()))
                .toList();
        if (values.isEmpty()) {
            errors.add(BulkForm.SELECT_ITEMS);
        } else if (items.size() != values.size()) {
            errors.add("Choose items from the listing.");
        }
        if (!errors.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            model.addAttribute("title", "The action was not applied");
            model.addAttribute("errors", errors);
            model.addAttribute("cancelPath", returnTo);
            return "admin/bulk-operation";
        }
        if (bulk.destructive(action.get()) && !"true".equals(confirmed)) {
            List<EntityData> chosen = bulk.load(items);
            model.addAttribute("title", "Apply " + action.get().label() + " to " + chosen.size()
                    + (chosen.size() == 1 ? " item?" : " items?"));
            model.addAttribute("labels", chosen.stream().map(EntityData::label).toList());
            model.addAttribute("action", BulkForm.path(view, display));
            model.addAttribute("actionId", action.get().id());
            model.addAttribute("items", values);
            model.addAttribute("destination", returnTo);
            model.addAttribute("confirmLabel", action.get().label());
            model.addAttribute("cancelPath", returnTo);
            return "admin/bulk-operation";
        }
        return "redirect:" + BatchController.pathFor(bulk.start(action.get(), items, returnTo));
    }
}
