package dev.springdrop.web;

import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormOutcome;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Serves any registered form. A browser gets a themed page; htmx gets the form
 * markup on its own and swaps it in place. The same POST handles both, so a form
 * behaves the same way with scripting off: it re-renders with its errors, or
 * goes back to the form once it has submitted.
 */
@Controller
public class FormController {

    public static final String PATH = "/form/{formId}";

    private static final String HTMX_HEADER = "HX-Request";

    private static final String CSRF_PARAMETER = "_csrf";

    private final FormBuilder forms;

    public FormController(FormBuilder forms) {
        this.forms = forms;
    }

    @GetMapping(PATH)
    public String show(@PathVariable String formId, Model model) {
        model.addAttribute("formId", formId);
        model.addAttribute("formMarkup", forms.render(formId));
        return "form/page";
    }

    @PostMapping(PATH)
    @ResponseBody
    public Object submit(
            @PathVariable String formId,
            @RequestParam Map<String, String> submitted,
            HttpServletRequest request) {

        FormOutcome outcome = forms.handle(formId, values(submitted));
        String destination = PATH.replace("{formId}", formId);

        if (outcome.submitted()) {
            return isHtmx(request)
                    ? ResponseEntity.noContent().header("HX-Redirect", destination).build()
                    : new RedirectView(destination);
        }
        if (isHtmx(request)) {
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(outcome.markup());
        }
        return new ModelAndView("form/page", Map.of("formId", formId, "formMarkup", outcome.markup()));
    }

    private static boolean isHtmx(HttpServletRequest request) {
        return request.getHeader(HTMX_HEADER) != null;
    }

    /** The submitted values, without the token the security layer already checked. */
    private static Map<String, Object> values(Map<String, String> submitted) {
        Map<String, Object> values = new LinkedHashMap<>(submitted);
        values.remove(CSRF_PARAMETER);
        return values;
    }
}
