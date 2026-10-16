package dev.springdrop.kernel.install;

import dev.springdrop.kernel.config.ConfigStore;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.file.FileItem;
import dev.springdrop.kernel.file.FileService;
import dev.springdrop.kernel.file.ManagedFile;
import dev.springdrop.kernel.file.UploadSchemes;
import dev.springdrop.kernel.media.MediaService;
import dev.springdrop.kernel.media.MediaType;
import dev.springdrop.kernel.media.MediaTypeManager;
import dev.springdrop.kernel.media.sources.ImageSource;
import dev.springdrop.kernel.menu.MenuConfig;
import dev.springdrop.kernel.menu.MenuLink;
import dev.springdrop.kernel.menu.MenuLinkContentService;
import dev.springdrop.kernel.node.NodeEntityType;
import dev.springdrop.kernel.node.NodeService;
import dev.springdrop.kernel.site.SiteInformation;
import dev.springdrop.kernel.taxonomy.TaxonomyEntityType;
import dev.springdrop.kernel.taxonomy.TaxonomyService;
import dev.springdrop.kernel.user.UserAccount;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * A site to try SpringDrop out on, not for production: the standard profile's
 * config with a notice saying so, and sample recipes with images and tags, an
 * image media item for each picture, an About page, and a main menu link to it.
 */
@Component
public class DemoProfile implements InstallProfile {

    public static final String ID = "demo";

    static final String SLOGAN = "A demo site with sample content, not for production";

    /** A sample recipe: its title, what it says, its tags, and the color of its picture. */
    private record Recipe(String title, String body, List<String> tags, Color color) {
    }

    private static final List<Recipe> RECIPES = List.of(
            new Recipe("Overnight sourdough loaf", "Mix the starter, flour, water, and salt in the evening, shape "
                    + "the dough in the morning, and bake it in a covered pot for 45 minutes.",
                    List.of("Baking", "Vegetarian"), new Color(0xC6, 0x8E, 0x55)),
            new Recipe("Roasted squash soup", "Roast the squash with onion and garlic until soft, then blend it "
                    + "with stock and a little cream.", List.of("Seasonal", "Vegetarian"), new Color(0xE0, 0x8A, 0x2E)),
            new Recipe("Apple and oat crumble", "Slice the apples into a dish, cover them with oats, butter, and "
                    + "brown sugar rubbed together, and bake until golden.", List.of("Baking", "Seasonal"),
                    new Color(0x9C, 0xB0, 0x4A)));

    private final StandardProfile standard;
    private final TaxonomyService taxonomy;
    private final NodeService nodes;
    private final MediaService media;
    private final MediaTypeManager mediaTypes;
    private final FileService files;
    private final UploadSchemes uploadSchemes;
    private final MenuLinkContentService menuLinks;
    private final ConfigStore config;

    public DemoProfile(StandardProfile standard, TaxonomyService taxonomy, NodeService nodes, MediaService media,
            MediaTypeManager mediaTypes, FileService files, UploadSchemes uploadSchemes,
            MenuLinkContentService menuLinks, ConfigStore config) {
        this.standard = standard;
        this.taxonomy = taxonomy;
        this.nodes = nodes;
        this.media = media;
        this.mediaTypes = mediaTypes;
        this.files = files;
        this.uploadSchemes = uploadSchemes;
        this.menuLinks = menuLinks;
        this.config = config;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String label() {
        return "Demo";
    }

    @Override
    public String description() {
        return "Sample recipes, tags, images, and menus to try SpringDrop out on. Not for production.";
    }

    @Override
    public int weight() {
        return 20;
    }

    @Override
    public List<String> modules() {
        return List.of();
    }

    @Override
    public List<String> configLocations() {
        return List.of(InstallProfile.configLocationOf(StandardProfile.ID), InstallProfile.configLocationOf(ID));
    }

    @Override
    public void install() {
        standard.install();
        MediaType images = mediaTypes.find(ImageSource.ID).orElseGet(() -> media.createType(ImageSource.ID, "Image",
                "Photos and other pictures.", ImageSource.ID));
        Map<String, Long> terms = new LinkedHashMap<>();
        RECIPES.stream().flatMap(recipe -> recipe.tags().stream()).distinct().forEach(tag -> terms.put(tag,
                ((Number) taxonomy.save(EntityData.of(TaxonomyEntityType.ID, null, "tags", tag,
                        Map.of(BaseFieldDefinition.STATUS, true))).id()).longValue()));
        for (Recipe recipe : RECIPES) {
            ManagedFile picture = picture(recipe);
            Map<String, Object> image = Map.of(FileItem.TARGET_ID, picture.id(), FileItem.ALT, recipe.title());
            EntityData item = media.create(images, UserAccount.ADMINISTRATOR_ID);
            Map<String, Object> itemFields = new LinkedHashMap<>(item.fields());
            itemFields.put(images.sourceField(), image);
            media.save(new EntityData(item.entityType(), null, null, item.bundle(), recipe.title(),
                    item.langcode(), null, itemFields));
            List<Long> tags = new ArrayList<>(recipe.tags().stream().map(terms::get).toList());
            nodes.save(EntityData.of(NodeEntityType.ID, null, "article", recipe.title(), Map.of(
                    BaseFieldDefinition.STATUS, true, NodeEntityType.PROMOTE, true,
                    "body", Map.of("value", "<p>" + recipe.body() + "</p>", "summary", "", "format", "basic_html"),
                    "field_tags", tags, "field_image", image)), UserAccount.ADMINISTRATOR_ID);
        }
        EntityData about = nodes.save(EntityData.of(NodeEntityType.ID, null, "page", "About this demo", Map.of(
                BaseFieldDefinition.STATUS, true,
                "body", Map.of("value", "<p>This site was installed with the demo profile. Its recipes, tags, and "
                        + "pictures are samples for trying SpringDrop out, and it is not for production.</p>",
                        "summary", "", "format", "basic_html"))), UserAccount.ADMINISTRATOR_ID);
        menuLinks.save(new MenuLink(null, MenuConfig.MAIN, "About this demo", "", NodeEntityType.path(about.id()),
                null, 10, false, true, null));
        SiteInformation site = config.read(SiteInformation.CONFIG_NAME, SiteInformation.class,
                SiteInformation.DEFAULTS);
        config.save(SiteInformation.CONFIG_NAME, new SiteInformation(site.name(), SLOGAN, site.mail(), site.url(),
                site.frontPage()));
    }

    /** A plain picture in the recipe's color, kept as the site keeps uploads. */
    private ManagedFile picture(Recipe recipe) {
        BufferedImage picture = new BufferedImage(640, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D canvas = picture.createGraphics();
        canvas.setColor(recipe.color());
        canvas.fillRect(0, 0, 640, 400);
        canvas.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            ImageIO.write(picture, "png", bytes);
            return files.store(bytes.toByteArray(), recipe.title().toLowerCase(Locale.ROOT)
                    .replace(' ', '-') + ".png", uploadSchemes.defaultScheme(), UserAccount.ADMINISTRATOR_ID);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }
}
