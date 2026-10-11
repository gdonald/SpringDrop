package dev.springdrop.web;

import dev.springdrop.kernel.entity.EntityKind;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.rest.RestResourceConfig;
import dev.springdrop.kernel.rest.RestResourceManager;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Which content entity types are served over REST: a box for each method and
 * format of each type. A type with no method ticked is not served, and a type
 * with methods but no format is served as JSON.
 */
@Controller
public class RestResourceController {

    public static final String PATH = "/admin/config/services/rest";

    private final RestResourceManager resources;
    private final EntityTypeManager entityTypes;

    public RestResourceController(RestResourceManager resources, EntityTypeManager entityTypes) {
        this.resources = resources;
        this.entityTypes = entityTypes;
    }

    /** The name of the box for one method or format of one entity type. */
    public static String boxName(String entityType, String choice) {
        return entityType + "__" + choice;
    }

    /** One entity type's row: what it enables now. */
    public record Row(String entityType, List<String> methods, List<String> formats) {
    }

    @GetMapping(PATH)
    public String form(Model model) {
        model.addAttribute("title", "REST resources");
        model.addAttribute("rows", contentTypes().stream().map(type -> resources.find(type.id())
                .map(resource -> new Row(type.id(), resource.methods(), resource.formats()))
                .orElse(new Row(type.id(), List.of(), List.of()))).toList());
        model.addAttribute("methods", RestResourceConfig.ALL_METHODS);
        model.addAttribute("formats", resources.formats());
        model.addAttribute("action", PATH);
        return "admin/rest-resources";
    }

    @PostMapping(PATH)
    public String save(@RequestParam Map<String, String> submitted) {
        for (EntityType type : contentTypes()) {
            List<String> methods = RestResourceConfig.ALL_METHODS.stream()
                    .filter(method -> submitted.containsKey(boxName(type.id(), method))).toList();
            List<String> formats = resources.formats().stream()
                    .filter(format -> submitted.containsKey(boxName(type.id(), format))).toList();
            if (methods.isEmpty()) {
                resources.delete(type.id());
            } else {
                resources.save(new RestResourceConfig(type.id(), methods,
                        formats.isEmpty() ? List.of("json") : formats));
            }
        }
        return "redirect:" + PATH;
    }

    private List<EntityType> contentTypes() {
        return entityTypes.all().stream().filter(type -> type.kind() == EntityKind.CONTENT)
                .sorted(Comparator.comparing(EntityType::id)).toList();
    }
}
