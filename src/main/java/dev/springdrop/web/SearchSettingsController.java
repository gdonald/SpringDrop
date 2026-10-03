package dev.springdrop.web;

import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.SelectOption;
import dev.springdrop.kernel.search.SearchIndexer;
import dev.springdrop.kernel.search.SearchService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Chooses the backend the site searches through, and builds its index again. */
@Controller
public class SearchSettingsController {

    public static final String PATH = "/admin/config/search/settings";

    public static final String ADMINISTER_SEARCH = "administer search";

    public static final String BACKEND = "backend";

    private final SearchService search;
    private final SearchIndexer indexer;
    private final FormBuilder formBuilder;
    private final FormRenderer renderer;

    public SearchSettingsController(SearchService search, SearchIndexer indexer, FormBuilder formBuilder,
            FormRenderer renderer) {
        this.search = search;
        this.indexer = indexer;
        this.formBuilder = formBuilder;
        this.renderer = renderer;
    }

    @GetMapping(PATH)
    public String form(Model model) {
        return page(settingsForm(search.backend().id()), Map.of(), model);
    }

    @PostMapping(PATH)
    public String save(@RequestParam Map<String, String> submitted, Model model) {
        String backend = submitted.getOrDefault(BACKEND, "");
        FormElement tree = settingsForm(backend);
        FormState state = formBuilder.validate(tree, new LinkedHashMap<>(submitted));
        if (!backend.isEmpty() && !search.backendIds().contains(backend)) {
            state.error(BACKEND, "Choose a backend.");
        }
        if (state.hasErrors()) {
            return page(tree, state.errors(), model);
        }
        indexer.switchBackend(backend);
        return "redirect:" + PATH;
    }

    private FormElement settingsForm(String backend) {
        return FormElement.of(ElementType.CONTAINER, "search-settings")
                .child(FormElement.of(ElementType.SELECT, BACKEND).label("Search backend").markRequired()
                        .value(backend)
                        .description("Saving builds the chosen backend's index again from every searchable "
                                + "entity.")
                        .options(search.backendIds().stream().map(id -> new SelectOption(id, id)).toList()))
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "save").label("Save and rebuild the index")));
    }

    private String page(FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", "Search settings");
        model.addAttribute("description", "The site searches through " + search.backend().id() + ". "
                + indexer.pending() + " entities wait to be indexed.");
        model.addAttribute("action", PATH);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/block-form";
    }
}
