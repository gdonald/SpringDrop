package dev.springdrop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import dev.springdrop.kernel.entity.BundleDefinition;
import dev.springdrop.kernel.entity.BundleManager;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.FieldConfigManager;
import dev.springdrop.kernel.field.FieldInstanceConfig;
import dev.springdrop.kernel.field.FieldStorageConfig;
import dev.springdrop.kernel.field.display.FieldDisplaySlot;
import dev.springdrop.kernel.field.display.ViewDisplayConfig;
import dev.springdrop.kernel.field.display.ViewDisplayManager;
import dev.springdrop.kernel.field.formatter.FormatterContext;
import dev.springdrop.kernel.field.formatter.types.LinkFormatter;
import dev.springdrop.kernel.file.FileEntityType;
import dev.springdrop.kernel.file.FileItem;
import dev.springdrop.kernel.file.FileSchemes;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.FileUsageService;
import dev.springdrop.kernel.file.ImageFieldType;
import dev.springdrop.kernel.file.ImageFormatter;
import dev.springdrop.kernel.file.ImageSize;
import dev.springdrop.kernel.file.ManagedFile;
import dev.springdrop.kernel.image.EffectConfig;
import dev.springdrop.kernel.image.ImageMarkup;
import dev.springdrop.kernel.image.ImageStyle;
import dev.springdrop.kernel.image.ImageStyleManager;
import dev.springdrop.kernel.image.ResponsiveImageFormatter;
import dev.springdrop.kernel.image.ResponsiveImageMapping;
import dev.springdrop.kernel.image.ResponsiveImageStyle;
import dev.springdrop.kernel.image.ResponsiveImageStyleManager;
import dev.springdrop.kernel.image.effects.ScaleEffect;
import dev.springdrop.kernel.theme.Breakpoint;
import dev.springdrop.kernel.theme.BreakpointGroup;
import dev.springdrop.kernel.theme.BreakpointManager;
import dev.springdrop.kernel.theme.Theme;
import dev.springdrop.support.AbstractIntegrationTest;
import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ResponsiveImageIntegrationTest.ExhibitTypes.class)
class ResponsiveImageIntegrationTest extends AbstractIntegrationTest {

    record Exhibit(long id, String label) {
    }

    static final EntityType EXHIBIT = EntityType.content("exhibit", Exhibit.class)
            .withBundles("type", "exhibit_type")
            .withLinks(Map.of("canonical", "/exhibit/{exhibit}"));

    static final EntityType EXHIBIT_TYPE = EntityType.config("exhibit_type", Exhibit.class);

    private static final String BUNDLE = "painting";

    private static final String HERO = "hero";

    private static final String NARROW = "narrow_test";

    private static final String WIDE = "wide_test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ResponsiveImageStyleManager responsiveStyles;

    @Autowired
    private ImageStyleManager imageStyles;

    @Autowired
    private BreakpointManager breakpoints;

    @Autowired
    private FileService files;

    @Autowired
    private FileUsageService usage;

    @Autowired
    private EntityQueryExecutor queries;

    @Autowired
    private ImageFormatter imageFormatter;

    @Autowired
    private ResponsiveImageFormatter responsiveFormatter;

    @Autowired
    private FieldConfigManager fields;

    @Autowired
    private BundleManager bundles;

    @Autowired
    private EntityTypeManager entityTypeManager;

    @Autowired
    private ViewDisplayManager viewDisplays;

    private ManagedFile photo;

    @BeforeEach
    void twoStylesAndAPhoto() throws Exception {
        imageStyles.save(new ImageStyle(NARROW, "Narrow", List.of(
                new EffectConfig("scale", ScaleEffect.ID, 0, Map.of("width", 400)))));
        imageStyles.save(new ImageStyle(WIDE, "Wide test", List.of(
                new EffectConfig("scale", ScaleEffect.ID, 0, Map.of("width", 800)))));
        byte[] content = new byte[16];
        photo = files.store(new ByteArrayInputStream(content), "portrait.png", content.length, FileSchemes.PUBLIC, 5L);
        entityTypeManager.installStorage(EXHIBIT.id());
        bundles.save(EXHIBIT.id(), new BundleDefinition(BUNDLE, "Painting"));
        fields.createStorage(new FieldStorageConfig("image", EXHIBIT.id(), ImageFieldType.ID, 1, Map.of()));
        fields.createInstance(FieldInstanceConfig.of("image", EXHIBIT.id(), BUNDLE, "Image"));
        fields.createStorage(new FieldStorageConfig("website", EXHIBIT.id(), "link", 1, Map.of()));
        fields.createInstance(FieldInstanceConfig.of("website", EXHIBIT.id(), BUNDLE, "Website"));
    }

    @AfterEach
    void removeEverything() {
        responsiveStyles.all().forEach(style -> responsiveStyles.delete(style.id()));
        imageStyles.delete(NARROW);
        imageStyles.delete(WIDE);
        viewDisplays.find(EXHIBIT.id(), BUNDLE, ViewDisplayConfig.DEFAULT_MODE)
                .ifPresent(display -> viewDisplays.delete(EXHIBIT.id(), BUNDLE, ViewDisplayConfig.DEFAULT_MODE));
        fields.fieldNames(EXHIBIT.id(), BUNDLE).forEach(field -> fields.deleteStorage(EXHIBIT.id(), field));
        bundles.delete(EXHIBIT.id(), BUNDLE);
        queries.query(FileEntityType.ID).ids().forEach(id -> {
            long fileId = ((Number) id).longValue();
            usage.usages(fileId).forEach(use -> usage.remove(fileId, use.module(), use.type(), use.id(), true));
            files.delete(fileId);
        });
    }

    private ResponsiveImageStyle hero(List<ResponsiveImageMapping> mappings) {
        return new ResponsiveImageStyle(HERO, "Hero", Theme.FRONT_END, NARROW, mappings);
    }

    private static String breakpoint(String size) {
        return Theme.FRONT_END + "." + size;
    }

    private static Map<String, Object> value(ManagedFile file) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put(FileItem.TARGET_ID, file.id());
        value.put(FileItem.ALT, "Portrait of Edith");
        value.put(FileItem.WIDTH, 1600);
        value.put(FileItem.HEIGHT, 1200);
        return value;
    }

    private FormatterContext context(Map<String, Object> settings, boolean withEntity) {
        FormatterContext context = new FormatterContext(fields.findStorage(EXHIBIT.id(), "image").orElseThrow(),
                fields.findInstance(EXHIBIT.id(), BUNDLE, "image").orElseThrow(), settings);
        return withEntity ? context.withEntity(EntityData.of(EXHIBIT.id(), 12L, BUNDLE, "Portrait", Map.of()))
                : context;
    }

    @Test
    void theFrontEndThemeDeclaresItsBootstrapBreakpoints() {
        BreakpointGroup group = breakpoints.group(Theme.FRONT_END).orElseThrow();

        assertThat(group.breakpoints()).extracting(Breakpoint::mediaQuery).containsExactly(Breakpoint.ALL,
                "(min-width: 576px)", "(min-width: 768px)", "(min-width: 992px)", "(min-width: 1200px)",
                "(min-width: 1400px)");
        assertThat(group.breakpoint(breakpoint("md"))).map(Breakpoint::multipliers).contains(List.of("1x", "2x"));
        assertThat(group.breakpoint("nowhere")).isEmpty();
        assertThat(breakpoints.groups()).extracting(BreakpointGroup::id).contains(Theme.FRONT_END);
    }

    @Test
    void aResponsiveImageFieldRendersAPictureWithTheMappedSourcesPerBreakpoint() {
        responsiveStyles.save(hero(List.of(
                ResponsiveImageMapping.single(breakpoint("xs"), "1x", NARROW),
                ResponsiveImageMapping.single(breakpoint("xs"), "2x", WIDE),
                ResponsiveImageMapping.bySize(breakpoint("lg"), "(min-width: 1200px) 50vw, 100vw",
                        List.of(NARROW, WIDE)))));

        Document page = Jsoup.parseBodyFragment(responsiveFormatter.render(
                context(Map.of(ResponsiveImageFormatter.RESPONSIVE_IMAGE_STYLE, HERO), false), value(photo)));

        List<Element> sources = page.select("picture > source");
        assertThat(sources).hasSize(2);
        assertThat(sources.get(0).attr("media")).isEqualTo("(min-width: 992px)");
        assertThat(sources.get(0).attr("srcset")).isEqualTo(imageStyles.url(NARROW, photo) + " 400w, "
                + imageStyles.url(WIDE, photo) + " 800w");
        assertThat(sources.get(0).attr("sizes")).isEqualTo("(min-width: 1200px) 50vw, 100vw");
        assertThat(sources.get(1).hasAttr("media")).isFalse();
        assertThat(sources.get(1).attr("srcset")).isEqualTo(imageStyles.url(NARROW, photo) + " 1x, "
                + imageStyles.url(WIDE, photo) + " 2x");
        assertThat(sources.get(1).attr("type")).isEqualTo("image/png");
        Element img = page.selectFirst("picture > img");
        assertThat(img.attr("src")).isEqualTo(imageStyles.url(NARROW, photo));
        assertThat(List.of(img.attr("width"), img.attr("height"), img.attr("alt"), img.attr("loading")))
                .containsExactly("400", "300", "Portrait of Edith", "lazy");
    }

    @Test
    void mappingsToStylesTheSiteLacksAreLeftOutAndTheOriginalCanBeMapped() {
        responsiveStyles.save(new ResponsiveImageStyle(HERO, "Hero", Theme.FRONT_END, "retired", List.of(
                ResponsiveImageMapping.single(breakpoint("sm"), "1x", "retired"),
                ResponsiveImageMapping.single(breakpoint("md"), "1x", ResponsiveImageStyle.ORIGINAL),
                ResponsiveImageMapping.bySize(breakpoint("xl"), "", List.of("retired")))));

        Document page = Jsoup.parseBodyFragment(responsiveStyles.render(responsiveStyles.find(HERO).orElseThrow(),
                photo, new ImageSize(1600, 1200), " alt=\"\""));

        assertThat(page.select("source")).hasSize(2);
        assertThat(page.select("source").get(0).attr("srcset")).isEqualTo(files.url(photo) + " 1600w");
        assertThat(page.select("source").get(0).hasAttr("sizes")).isFalse();
        assertThat(page.select("source").get(1).attr("srcset")).isEqualTo(files.url(photo) + " 1x");
        assertThat(page.selectFirst("img").attr("src")).isEqualTo(files.url(photo));
        assertThat(page.selectFirst("img").attr("width")).isEqualTo("1600");
    }

    @Test
    void anImageOfUnknownSizeOffersNoWidthsAndAGroupTheSiteLacksOffersNoSources() {
        responsiveStyles.save(hero(List.of(ResponsiveImageMapping.bySize(breakpoint("lg"), "100vw", List.of(NARROW)))));
        ResponsiveImageStyle orphaned = new ResponsiveImageStyle(HERO, "Hero", "retired_theme", NARROW, List.of(
                ResponsiveImageMapping.single(breakpoint("lg"), "1x", NARROW)));

        Document unknownSize = Jsoup.parseBodyFragment(responsiveStyles.render(
                responsiveStyles.find(HERO).orElseThrow(), photo, null, ""));
        Document noGroup = Jsoup.parseBodyFragment(responsiveStyles.render(orphaned, photo, null, ""));

        assertThat(unknownSize.select("source")).isEmpty();
        assertThat(unknownSize.selectFirst("img").hasAttr("width")).isFalse();
        assertThat(noGroup.select("source")).isEmpty();
    }

    @Test
    void withoutAResponsiveStyleTheOriginalIsDrawn() {
        String markup = responsiveFormatter.render(context(Map.of(), false), value(photo));

        assertThat(Jsoup.parseBodyFragment(markup).selectFirst("img").attr("src")).isEqualTo(files.url(photo));
        assertThat(responsiveFormatter.render(context(Map.of(), false), Map.of(FileItem.TARGET_ID, 999_999L)))
                .isEmpty();
    }

    @Test
    void anImageIsDrawnWithItsStyleAtTheSizeTheStyleDrawsIt() {
        Element img = Jsoup.parseBodyFragment(imageFormatter.render(
                context(Map.of(ImageFormatter.IMAGE_STYLE, NARROW, ImageMarkup.IMAGE_LOADING, ImageMarkup.EAGER),
                        false), value(photo))).selectFirst("img");

        assertThat(img.attr("src")).isEqualTo(imageStyles.url(NARROW, photo));
        assertThat(List.of(img.attr("width"), img.attr("height"), img.attr("loading")))
                .containsExactly("400", "300", "eager");
    }

    @Test
    void anImageInAStyleTheSiteLacksIsDrawnAsTheOriginal() {
        Element img = Jsoup.parseBodyFragment(imageFormatter.render(
                context(Map.of(ImageFormatter.IMAGE_STYLE, "retired"), false), value(photo))).selectFirst("img");

        assertThat(img.attr("src")).isEqualTo(files.url(photo));
        assertThat(img.attr("width")).isEqualTo("1600");
    }

    @Test
    void anImageLinksToItsContentOrItsFileWhenSetTo() {
        Document toContent = Jsoup.parseBodyFragment(imageFormatter.render(
                context(Map.of(ImageMarkup.IMAGE_LINK, ImageMarkup.LINK_CONTENT), true), value(photo)));
        Document toFile = Jsoup.parseBodyFragment(responsiveFormatter.render(
                context(Map.of(ImageMarkup.IMAGE_LINK, ImageMarkup.LINK_FILE), false), value(photo)));
        Document withoutEntity = Jsoup.parseBodyFragment(imageFormatter.render(
                context(Map.of(ImageMarkup.IMAGE_LINK, ImageMarkup.LINK_CONTENT), false), value(photo)));
        Document unlinked = Jsoup.parseBodyFragment(imageFormatter.render(
                context(Map.of(ImageMarkup.IMAGE_LINK, "elsewhere"), true), value(photo)));

        assertThat(toContent.selectFirst("a > img").parent().attr("href")).isEqualTo("/exhibit/12");
        assertThat(toFile.selectFirst("a").attr("href")).isEqualTo(files.url(photo));
        assertThat(withoutEntity.select("a")).isEmpty();
        assertThat(unlinked.select("a")).isEmpty();
    }

    @Test
    void anEntityOfATypeTheSiteLacksGetsNoLink() {
        FormatterContext orphan = context(Map.of(ImageMarkup.IMAGE_LINK, ImageMarkup.LINK_CONTENT), false)
                .withEntity(EntityData.of("retired_type", 3L, BUNDLE, "Old", Map.of()));

        assertThat(Jsoup.parseBodyFragment(imageFormatter.render(orphan, value(photo))).select("a")).isEmpty();
    }

    @Test
    void theImageFormattersOfferTheirStylesLinksAndLoading() {
        responsiveStyles.save(hero(List.of()));

        assertThat(imageFormatter.settingsForm("image_settings_", Map.of()).get(0).options())
                .extracting(option -> option.value()).contains("", NARROW, WIDE);
        assertThat(responsiveFormatter.settingsForm("image_settings_", Map.of()).get(0).options())
                .extracting(option -> option.value()).containsExactly(HERO);
        assertThat(imageFormatter.settingsValues("p_", Map.of("p_image_style", NARROW, "p_image_link", "file",
                "p_image_loading", "eager"))).isEqualTo(Map.of(ImageFormatter.IMAGE_STYLE, NARROW,
                ImageMarkup.IMAGE_LINK, "file", ImageMarkup.IMAGE_LOADING, "eager"));
        assertThat(imageFormatter.settingsValues("p_", Map.of("p_image_style", "retired")))
                .isEqualTo(Map.of(ImageFormatter.IMAGE_STYLE, "", ImageMarkup.IMAGE_LINK, "",
                        ImageMarkup.IMAGE_LOADING, "lazy"));
        assertThat(responsiveFormatter.settingsValues("p_", Map.of("p_responsive_image_style", HERO)))
                .containsEntry(ResponsiveImageFormatter.RESPONSIVE_IMAGE_STYLE, HERO);
        assertThat(responsiveFormatter.settingsValues("p_", Map.of()))
                .containsEntry(ResponsiveImageFormatter.RESPONSIVE_IMAGE_STYLE, "");
        assertThat(ImageMarkup.storedSize(Map.of(FileItem.WIDTH, 5))).isEmpty();
        assertThat(responsiveFormatter.id()).isEqualTo(ResponsiveImageFormatter.ID);
        assertThat(new LinkFormatter(null).settingsValues("p_", Map.of("p_trim_length", "5"))).isEmpty();
    }

    private MockHttpServletResponse asAdministrator(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(user("admin").authorities(
                new SimpleGrantedAuthority(ResponsiveImageStyleController.ADMINISTER_RESPONSIVE_IMAGES),
                new SimpleGrantedAuthority("administer fields")))).andReturn().getResponse();
    }

    private MockHttpServletResponse submit(String path, Map<String, String> fields) throws Exception {
        MockHttpServletRequestBuilder request = post(path).with(csrf());
        fields.forEach(request::param);
        return asAdministrator(request);
    }

    @Test
    void manageDisplaySavesTheSettingsOfTheChosenFormatter() throws Exception {
        String path = DisplayUiController.viewDisplayPath(EXHIBIT.id(), BUNDLE);
        Document form = Jsoup.parse(asAdministrator(get(path)).getContentAsString());
        assertThat(form.select("select[name=image_settings_image_style]")).hasSize(1);

        submit(path, Map.of("image_shown", "true", "image_formatter", ImageFormatter.ID, "image_weight", "0",
                "image_settings_image_style", NARROW, "image_settings_image_link", "content",
                "website_shown", "true", "website_formatter", "unknown_formatter", "website_weight", "1"));

        Map<String, FieldDisplaySlot> slots = viewDisplays.find(EXHIBIT.id(), BUNDLE, ViewDisplayConfig.DEFAULT_MODE)
                .orElseThrow().slots();
        assertThat(slots.get("image").settings()).containsEntry(ImageFormatter.IMAGE_STYLE, NARROW)
                .containsEntry(ImageMarkup.IMAGE_LINK, "content");
        assertThat(slots.get("website").settings()).isEmpty();
    }

    @Test
    void manageDisplayKeepsTheSettingsOfAFormatterWithoutAForm() throws Exception {
        String path = DisplayUiController.viewDisplayPath(EXHIBIT.id(), BUNDLE);
        viewDisplays.save(ViewDisplayConfig.of(EXHIBIT.id(), BUNDLE, ViewDisplayConfig.DEFAULT_MODE)
                .with(new FieldDisplaySlot("website", LinkFormatter.ID, 0, Map.of(LinkFormatter.TRIM_LENGTH, 20))));

        Document form = Jsoup.parse(asAdministrator(get(path)).getContentAsString());
        submit(path, Map.of("website_shown", "true", "website_formatter", LinkFormatter.ID, "website_weight", "0",
                "image_shown", "true", "image_formatter", ResponsiveImageFormatter.ID, "image_weight", "1"));

        assertThat(form.select("[name^=website_settings_]")).isEmpty();
        Map<String, FieldDisplaySlot> slots = viewDisplays.find(EXHIBIT.id(), BUNDLE, ViewDisplayConfig.DEFAULT_MODE)
                .orElseThrow().slots();
        assertThat(slots.get("website").settings()).containsEntry(LinkFormatter.TRIM_LENGTH, 20);
        assertThat(slots.get("image").settings()).containsEntry(ImageMarkup.IMAGE_LOADING, "lazy");

        submit(path, Map.of("website_shown", "true", "website_formatter", "string", "website_weight", "0"));
        assertThat(viewDisplays.find(EXHIBIT.id(), BUNDLE, ViewDisplayConfig.DEFAULT_MODE).orElseThrow().slots()
                .get("website").settings()).isEmpty();
    }

    @Test
    void manageDisplayShowsNoSettingsForAFormatterTheSiteLacks() throws Exception {
        viewDisplays.save(ViewDisplayConfig.of(EXHIBIT.id(), BUNDLE, ViewDisplayConfig.DEFAULT_MODE)
                .with(FieldDisplaySlot.of("image", "retired_formatter", 0)));

        Document form = Jsoup.parse(asAdministrator(get(DisplayUiController.viewDisplayPath(EXHIBIT.id(), BUNDLE)))
                .getContentAsString());

        assertThat(form.select("[name^=image_settings_]")).isEmpty();
    }

    @Test
    void aResponsiveStyleIsAddedThenMappedPerBreakpoint() throws Exception {
        assertThat(Jsoup.parse(asAdministrator(get(ResponsiveImageStyleController.PATH + "/add"))
                .getContentAsString()).select("select[name=breakpoint_group] option")).isNotEmpty();
        MockHttpServletResponse added = submit(ResponsiveImageStyleController.PATH + "/add", Map.of(
                "label", "Hero", "breakpoint_group", Theme.FRONT_END, "fallback_image_style", NARROW));
        assertThat(added.getRedirectedUrl()).isEqualTo(ResponsiveImageStyleController.managePath(HERO));

        String xs1 = ResponsiveImageStyleController.mappingPrefix(breakpoint("xs"), "1x");
        String xs2 = ResponsiveImageStyleController.mappingPrefix(breakpoint("xs"), "2x");
        String lg = ResponsiveImageStyleController.mappingPrefix(breakpoint("lg"), "1x");
        String md = ResponsiveImageStyleController.mappingPrefix(breakpoint("md"), "1x");
        Map<String, String> mapping = new LinkedHashMap<>();
        mapping.put("label", "Hero banner");
        mapping.put("fallback_image_style", "retired");
        mapping.put(xs1 + "type", "image_style");
        mapping.put(xs1 + "image_style", NARROW);
        mapping.put(xs2 + "type", "image_style");
        mapping.put(xs2 + "image_style", "retired");
        mapping.put(lg + "type", "sizes");
        mapping.put(lg + "sizes", " 50vw ");
        mapping.put(lg + "sizes_style_" + NARROW, "true");
        mapping.put(lg + "sizes_style_" + WIDE, "true");
        mapping.put(md + "type", "sizes");
        mapping.put(md + "sizes", "100vw");
        submit(ResponsiveImageStyleController.managePath(HERO), mapping);

        ResponsiveImageStyle saved = responsiveStyles.find(HERO).orElseThrow();
        assertThat(saved.label()).isEqualTo("Hero banner");
        assertThat(saved.fallbackImageStyle()).isEqualTo(ResponsiveImageStyle.ORIGINAL);
        assertThat(saved.mappings()).containsExactly(
                ResponsiveImageMapping.single(breakpoint("xs"), "1x", NARROW),
                ResponsiveImageMapping.bySize(breakpoint("lg"), "50vw", List.of(NARROW, WIDE)));
        Document edit = Jsoup.parse(asAdministrator(get(ResponsiveImageStyleController.managePath(HERO)))
                .getContentAsString());
        assertThat(edit.selectFirst("details:has(select[name=" + xs1 + "type])").hasAttr("open")).isTrue();
        assertThat(edit.selectFirst("select[name=" + xs2 + "type]").select("option")).hasSize(2);
        assertThat(edit.text()).contains("Breakpoint group: Front end");
    }

    @Test
    void sizesAreOnlyTakenAtTheFirstDensityAndTheOriginalCanBeTheFallback() throws Exception {
        responsiveStyles.save(hero(List.of()));
        String xs2 = ResponsiveImageStyleController.mappingPrefix(breakpoint("xs"), "2x");

        submit(ResponsiveImageStyleController.managePath(HERO), Map.of("label", "Hero",
                "fallback_image_style", ResponsiveImageStyle.ORIGINAL, xs2 + "type", "sizes", xs2 + "sizes", "50vw",
                xs2 + "sizes_style_" + NARROW, "true"));

        assertThat(responsiveStyles.find(HERO).orElseThrow().mappings()).isEmpty();
        assertThat(responsiveStyles.find(HERO).orElseThrow().fallbackImageStyle())
                .isEqualTo(ResponsiveImageStyle.ORIGINAL);
    }

    @Test
    void aMappingMissingWhatItsTypeNeedsIsRefused() throws Exception {
        responsiveStyles.save(hero(List.of()));
        String lg = ResponsiveImageStyleController.mappingPrefix(breakpoint("lg"), "1x");

        MockHttpServletResponse response = submit(ResponsiveImageStyleController.managePath(HERO), Map.of(
                "label", "Hero", "fallback_image_style", NARROW, lg + "type", "sizes", lg + "sizes", ""));

        assertThat(Jsoup.parse(response.getContentAsString()).text()).contains("This value is required.");
        assertThat(responsiveStyles.find(HERO).orElseThrow().mappings()).isEmpty();
    }

    @Test
    void aResponsiveStyleNeedsANameAndABreakpointGroup() throws Exception {
        MockHttpServletResponse response = submit(ResponsiveImageStyleController.PATH + "/add", Map.of(
                "label", "", "breakpoint_group", "retired_theme", "fallback_image_style", NARROW));

        assertThat(Jsoup.parse(response.getContentAsString()).text()).contains("This value is required.")
                .contains("Choose a breakpoint group.");
        assertThat(responsiveStyles.all()).isEmpty();
    }

    @Test
    void theListShowsEachResponsiveStyleWithItsBreakpointGroup() throws Exception {
        responsiveStyles.save(hero(List.of()));
        responsiveStyles.save(new ResponsiveImageStyle("orphan", "Orphan", "retired_theme", NARROW, List.of()));

        Document list = Jsoup.parse(asAdministrator(get(ResponsiveImageStyleController.PATH)).getContentAsString());

        assertThat(list.selectFirst("tr[data-responsive-style=hero]").text()).contains("Hero").contains("Front end");
        assertThat(list.selectFirst("tr[data-responsive-style=orphan]").text()).contains("retired_theme");
        assertThat(Jsoup.parse(asAdministrator(get(ResponsiveImageStyleController.managePath("orphan")))
                .getContentAsString()).text()).contains("Breakpoint group: retired_theme");
    }

    @Test
    void aResponsiveStyleIsDeletedThroughItsConfirmForm() throws Exception {
        responsiveStyles.save(hero(List.of()));

        assertThat(asAdministrator(get(ResponsiveImageStyleController.managePath(HERO) + "/delete"))
                .getContentAsString()).contains("Delete the responsive image style Hero?");
        submit(ResponsiveImageStyleController.managePath(HERO) + "/delete", Map.of());

        assertThat(responsiveStyles.find(HERO)).isEmpty();
        assertThat(asAdministrator(get(ResponsiveImageStyleController.managePath(HERO))).getStatus()).isEqualTo(404);
    }

    @Test
    void someoneWithoutThePermissionCannotManageResponsiveStyles() throws Exception {
        assertThat(mockMvc.perform(get(ResponsiveImageStyleController.PATH).with(user("visitor")))
                .andReturn().getResponse().getStatus()).isEqualTo(403);
    }

    @TestConfiguration
    static class ExhibitTypes {

        @Bean
        EntityTypeProvider exhibitTypes() {
            return () -> List.of(EXHIBIT, EXHIBIT_TYPE);
        }
    }
}
