package dev.springdrop.web;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.block.BlockPageRenderer;
import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityAccessHandler;
import dev.springdrop.kernel.entity.EntityAccessManager;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityValidationException;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.entity.query.Sort;
import dev.springdrop.kernel.field.FieldConstraintProvider;
import dev.springdrop.kernel.field.display.FormDisplayConfig;
import dev.springdrop.kernel.field.display.FormDisplayManager;
import dev.springdrop.kernel.field.display.ViewDisplayConfig;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.form.ElementType;
import dev.springdrop.kernel.form.FormBuilder;
import dev.springdrop.kernel.form.FormElement;
import dev.springdrop.kernel.form.FormRenderer;
import dev.springdrop.kernel.form.FormState;
import dev.springdrop.kernel.form.ValidationRule;
import dev.springdrop.kernel.image.ImageStyleManager;
import dev.springdrop.kernel.media.MediaEmbedFilter;
import dev.springdrop.kernel.media.MediaEntityType;
import dev.springdrop.kernel.media.MediaService;
import dev.springdrop.kernel.media.MediaType;
import dev.springdrop.kernel.media.MediaTypeManager;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.kernel.theme.PageChrome;
import dev.springdrop.kernel.theme.Tab;
import dev.springdrop.kernel.web.EntityNotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Adding, reading, changing, and removing media items, and the media overview.
 * Each step asks the item's access rules first.
 */
@Controller
public class MediaController {

    public static final String ADD_PATH = "/media/add";

    public static final String OVERVIEW_PATH = "/admin/content/media";

    public static final String NAME = "name";

    public static final String STATUS = "status";

    public static final String NEW_REVISION = "new_revision";

    private static final String TEXT_HTML = org.springframework.http.MediaType.TEXT_HTML_VALUE;

    /** The image style thumbnails are listed in. */
    public static final String THUMBNAIL_STYLE = "thumbnail";

    private final MediaTypeManager types;
    private final MediaService media;
    private final EntityAccessManager entityAccess;
    private final FormDisplayManager formDisplays;
    private final FormBuilder forms;
    private final FormRenderer renderer;
    private final ConfigStore configStore;
    private final BlockPageRenderer pages;
    private final EntityQueryExecutor queries;
    private final FileService files;
    private final ImageStyleManager imageStyles;

    public MediaController(MediaTypeManager types, MediaService media, EntityAccessManager entityAccess,
            FormDisplayManager formDisplays, FormBuilder forms, FormRenderer renderer, ConfigStore configStore,
            BlockPageRenderer pages, EntityQueryExecutor queries, FileService files, ImageStyleManager imageStyles) {
        this.types = types;
        this.media = media;
        this.entityAccess = entityAccess;
        this.formDisplays = formDisplays;
        this.forms = forms;
        this.renderer = renderer;
        this.configStore = configStore;
        this.pages = pages;
        this.queries = queries;
        this.files = files;
        this.imageStyles = imageStyles;
    }

    @GetMapping(ADD_PATH)
    public String chooseType(Model model) {
        model.addAttribute("title", "Add media");
        model.addAttribute("types", types.all().stream()
                .filter(type -> entityAccess.may(MediaEntityType.ID, type.id(), EntityAccessHandler.CREATE))
                .toList());
        model.addAttribute("addPath", ADD_PATH + "/");
        return "node/add";
    }

    @GetMapping(ADD_PATH + "/{type}")
    public String addForm(@PathVariable String type, Model model) {
        MediaType mediaType = creatableType(type);
        EntityData blank = media.create(mediaType, CurrentAccount.id());
        return formPage("Add " + mediaType.label(), ADD_PATH + "/" + type, mediaForm(blank, true), Map.of(), model);
    }

    @PostMapping(ADD_PATH + "/{type}")
    public String add(@PathVariable String type, @RequestParam Map<String, String> submitted, Model model) {
        MediaType mediaType = creatableType(type);
        return save(media.create(mediaType, CurrentAccount.id()), submitted, "Add " + mediaType.label(),
                ADD_PATH + "/" + type, model);
    }

    @GetMapping(value = "/media/{id}", produces = TEXT_HTML)
    @ResponseBody
    public String view(@PathVariable long id) {
        EntityData item = permitted(id, EntityAccessHandler.VIEW);
        SiteInformation site = configStore.read(SiteInformation.CONFIG_NAME, SiteInformation.class,
                SiteInformation.DEFAULTS);
        List<Tab> tabs = new ArrayList<>(List.of(new Tab("View", MediaEntityType.path(id), true)));
        if (entityAccess.may(MediaEntityType.ID, item, EntityAccessHandler.UPDATE)) {
            tabs.add(new Tab("Edit", MediaEntityType.editPath(id), false));
        }
        if (entityAccess.may(MediaEntityType.ID, item, EntityAccessHandler.DELETE)) {
            tabs.add(new Tab("Delete", MediaEntityType.path(id) + "/delete", false));
        }
        PageChrome chrome = PageChrome.of(site.name(), item.label()).withTabs(tabs);
        return pages.render(chrome, media.build(item, ViewDisplayConfig.FULL_MODE, true),
                BlockContext.of(MediaEntityType.path(id), item.label()).withRouteEntity(item)).html();
    }

    @GetMapping("/media/{id}/edit")
    public String editForm(@PathVariable long id, Model model) {
        EntityData item = permitted(id, EntityAccessHandler.UPDATE);
        return formPage("Edit " + item.label(), MediaEntityType.editPath(id), mediaForm(item, true), Map.of(), model);
    }

    @PostMapping("/media/{id}/edit")
    public String edit(@PathVariable long id, @RequestParam Map<String, String> submitted, Model model) {
        EntityData item = permitted(id, EntityAccessHandler.UPDATE);
        return save(item, submitted, "Edit " + item.label(), MediaEntityType.editPath(id), model);
    }

    /**
     * Saves the item once the form's rules and the item's constraints pass,
     * then shows it. Otherwise the form comes back with each error on its field.
     */
    private String save(EntityData base, Map<String, String> submitted, String title, String action, Model model) {
        Map<String, Object> values = new LinkedHashMap<>(submitted);
        Map<String, Object> fields = new LinkedHashMap<>(base.fields());
        formDisplays.shownFields(MediaEntityType.ID, base.bundle(), FormDisplayConfig.DEFAULT_MODE)
                .forEach(field -> fields.put(field, List.of()));
        fields.putAll(formDisplays.extract(MediaEntityType.ID, base.bundle(), FormDisplayConfig.DEFAULT_MODE,
                values));
        fields.put(BaseFieldDefinition.STATUS, submitted.containsKey(STATUS));
        EntityData candidate = new EntityData(MediaEntityType.ID, base.id(), base.uuid(), base.bundle(),
                submitted.getOrDefault(NAME, "").strip(), base.langcode(), null, fields);
        boolean newRevision = base.id() == null || submitted.containsKey(NEW_REVISION);
        FormElement tree = mediaForm(candidate, newRevision);
        FormState state = forms.validate(tree, values);
        if (state.hasErrors()) {
            return formPage(title, action, tree, state.errors(), model);
        }
        EntityData saved;
        try {
            saved = media.save(candidate, newRevision);
        } catch (EntityValidationException invalid) {
            Map<String, String> errors = new LinkedHashMap<>();
            invalid.violations().forEach(violation -> errors.putIfAbsent(
                    violation.propertyPath().substring(FieldConstraintProvider.FIELD_PATH_PREFIX.length()),
                    violation.message()));
            return formPage(title, action, tree, errors, model);
        }
        return "redirect:" + MediaEntityType.path(saved.id());
    }

    private FormElement mediaForm(EntityData item, boolean newRevision) {
        String cancel = (item.id() == null) ? ADD_PATH : MediaEntityType.path(item.id());
        FormElement form = FormElement.of(ElementType.CONTAINER, "media-form")
                .child(FormElement.of(ElementType.TEXTFIELD, NAME).label("Name")
                        .description("Left empty, the name comes from the media source.")
                        .value(item.label())
                        .rule(ValidationRule.maxLength(MediaService.MAX_NAME_LENGTH)))
                .child(formDisplays.buildContainer(MediaEntityType.ID, item.bundle(), FormDisplayConfig.DEFAULT_MODE,
                        item.fields()))
                .child(FormElement.of(ElementType.CHECKBOX, STATUS).label("Published")
                        .value(Boolean.TRUE.equals(item.fields().get(BaseFieldDefinition.STATUS))));
        if (item.id() != null) {
            form.child(FormElement.of(ElementType.CHECKBOX, NEW_REVISION).label("Create new revision")
                    .value(newRevision));
            form.child(FormElement.of(ElementType.TEXT, "embed_code")
                    .label("Embed code: " + MediaEmbedFilter.embedCode(item.uuid()))
                    .attribute("class", "font-monospace small text-body-secondary"));
        }
        return form.child(FormElement.of(ElementType.ACTIONS, "actions")
                .child(FormElement.of(ElementType.SUBMIT, "save").label("Save"))
                .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel").value(cancel)));
    }

    @GetMapping("/media/{id}/delete")
    public String confirmDelete(@PathVariable long id, Model model) {
        EntityData item = permitted(id, EntityAccessHandler.DELETE);
        model.addAttribute("title", "Delete the media item " + item.label() + "?");
        model.addAttribute("description", "Content referring to it no longer shows it. This action cannot be undone.");
        model.addAttribute("action", MediaEntityType.path(id) + "/delete");
        model.addAttribute("formMarkup", renderer.render(FormElement.of(ElementType.CONTAINER, "confirm")
                .child(FormElement.of(ElementType.ACTIONS, "actions")
                        .child(FormElement.of(ElementType.SUBMIT, "confirm").label("Delete")
                                .attribute("class", "btn btn-danger"))
                        .child(FormElement.of(ElementType.LINK, "cancel").label("Cancel")
                                .value(MediaEntityType.path(id))))));
        return "admin/block-form";
    }

    @PostMapping("/media/{id}/delete")
    public String delete(@PathVariable long id) {
        permitted(id, EntityAccessHandler.DELETE);
        media.delete(id);
        return "redirect:" + OVERVIEW_PATH;
    }

    public record MediaRow(long id, String label, String type, String thumbnail, boolean published, String changed,
            boolean editable) {
    }

    /** Every media item, newest change first, with its thumbnail. */
    @GetMapping(OVERVIEW_PATH)
    public String overview(Model model) {
        List<MediaRow> rows = new ArrayList<>();
        for (Object id : queries.query(MediaEntityType.ID).sort(Sort.descending(BaseFieldDefinition.CHANGED)).ids()) {
            media.find(((Number) id).longValue()).ifPresent(item -> rows.add(new MediaRow(
                    ((Number) item.id()).longValue(), item.label(),
                    types.find(item.bundle()).map(MediaType::label).orElse(item.bundle()),
                    thumbnailOf(item),
                    Boolean.TRUE.equals(item.fields().get(BaseFieldDefinition.STATUS)),
                    String.valueOf(item.fields().getOrDefault(BaseFieldDefinition.CHANGED, "")),
                    entityAccess.may(MediaEntityType.ID, item, EntityAccessHandler.UPDATE))));
        }
        model.addAttribute("title", "Media");
        model.addAttribute("rows", rows);
        model.addAttribute("addPath", ADD_PATH);
        return "admin/media";
    }

    /** The address of the item's thumbnail in the listing style, or blank when it has none. */
    private String thumbnailOf(EntityData item) {
        if (!(item.fields().get(MediaEntityType.THUMBNAIL) instanceof Number thumbnail)) {
            return "";
        }
        return files.find(thumbnail.longValue())
                .map(file -> imageStyles.find(THUMBNAIL_STYLE).map(style -> imageStyles.url(style.id(), file))
                        .orElseGet(() -> files.url(file)))
                .orElse("");
    }

    private MediaType creatableType(String type) {
        MediaType mediaType = types.find(type).orElseThrow(() -> new EntityNotFoundException("media type", type));
        if (!entityAccess.may(MediaEntityType.ID, type, EntityAccessHandler.CREATE)) {
            throw new AccessDeniedException("You may not create " + mediaType.label() + " media.");
        }
        return mediaType;
    }

    private EntityData permitted(long id, String operation) {
        EntityData item = media.find(id)
                .orElseThrow(() -> new EntityNotFoundException("media", String.valueOf(id)));
        if (!entityAccess.may(MediaEntityType.ID, item, operation)) {
            throw new AccessDeniedException("You may not " + operation + " this media.");
        }
        return item;
    }

    private String formPage(String title, String action, FormElement tree, Map<String, String> errors, Model model) {
        model.addAttribute("title", title);
        model.addAttribute("description", "A media item can be reused wherever content refers to media.");
        model.addAttribute("action", action);
        model.addAttribute("formMarkup", renderer.render(tree, errors));
        return "admin/block-form";
    }
}
