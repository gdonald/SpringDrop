package dev.springdrop.kernel.menu;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.entity.query.Sort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The menu links a site wrote, read and written as {@link MenuLink} so the tree
 * builder does not care which source a link came from. Their ids carry the
 * entity type as a prefix, which keeps them apart from the ids modules choose
 * for the links they declare in code.
 */
@Component
public class MenuLinkContentService {

    private static final String ID_PREFIX = MenuLinkContentEntityType.ID + ":";

    private final EntityCrudService entities;
    private final EntityQueryExecutor queries;
    private final EntityTypeManager entityTypeManager;

    public MenuLinkContentService(
            EntityCrudService entities,
            EntityQueryExecutor queries,
            EntityTypeManager entityTypeManager) {
        this.entities = entities;
        this.queries = queries;
        this.entityTypeManager = entityTypeManager;
    }

    /** Creates the table these links are stored in, the step install runs. */
    public void install() {
        entityTypeManager.installStorage(MenuLinkContentEntityType.ID);
    }

    /** The tree id of the stored link with this entity id. */
    public static String linkId(Object entityId) {
        return ID_PREFIX + entityId;
    }

    /** The entity id behind a tree id, or empty for a link declared in code. */
    public static Optional<Long> entityId(String treeId) {
        if (treeId == null || !treeId.startsWith(ID_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(treeId.substring(ID_PREFIX.length())));
    }

    /** Stores a link, creating it when its id is null and updating it otherwise. */
    public MenuLink save(MenuLink link) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(MenuLinkContentEntityType.MENU, link.menu());
        values.put(MenuLinkContentEntityType.LINK_URL, link.url());
        // The column holds an empty string for a link at the top of its menu,
        // since an entity's values may not be null.
        values.put(MenuLinkContentEntityType.PARENT, (link.parent() == null) ? "" : link.parent());
        values.put(MenuLinkContentEntityType.WEIGHT, link.weight());
        values.put(MenuLinkContentEntityType.EXPANDED, link.expanded());
        values.put(MenuLinkContentEntityType.DESCRIPTION, link.description());
        values.put(BaseFieldDefinition.STATUS, link.enabled());

        EntityData stored = entities.save(new EntityData(
                MenuLinkContentEntityType.ID,
                entityId(link.id()).orElse(null),
                null,
                null,
                link.title(),
                EntityData.DEFAULT_LANGCODE,
                null,
                values));
        return toLink(stored);
    }

    public Optional<MenuLink> find(long entityId) {
        return entities.load(MenuLinkContentEntityType.ID, entityId).map(MenuLinkContentService::toLink);
    }

    public void delete(long entityId) {
        entities.delete(MenuLinkContentEntityType.ID, entityId);
    }

    /** Every stored link in one menu, whether enabled or not. */
    public List<MenuLink> inMenu(String menuId) {
        List<MenuLink> links = new ArrayList<>();
        List<Object> ids = queries.query(MenuLinkContentEntityType.ID)
                .condition(Condition.equal(MenuLinkContentEntityType.MENU, menuId))
                .sort(Sort.ascending("id"))
                .ids();
        for (Object id : ids) {
            find(((Number) id).longValue()).ifPresent(links::add);
        }
        return links;
    }

    /**
     * Every column a stored link has was written when it was saved, so each one
     * is read back as the type it was written as.
     */
    private static MenuLink toLink(EntityData entity) {
        Map<String, Object> values = entity.fields();
        String parent = (String) values.get(MenuLinkContentEntityType.PARENT);
        return new MenuLink(
                linkId(entity.id()),
                (String) values.get(MenuLinkContentEntityType.MENU),
                entity.label(),
                (String) values.get(MenuLinkContentEntityType.DESCRIPTION),
                (String) values.get(MenuLinkContentEntityType.LINK_URL),
                parent.isEmpty() ? null : parent,
                ((Number) values.get(MenuLinkContentEntityType.WEIGHT)).intValue(),
                (Boolean) values.get(MenuLinkContentEntityType.EXPANDED),
                (Boolean) values.get(BaseFieldDefinition.STATUS),
                null);
    }
}
