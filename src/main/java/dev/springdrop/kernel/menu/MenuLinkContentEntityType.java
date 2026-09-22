package dev.springdrop.kernel.menu;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityType;
import dev.springdrop.kernel.entity.EntityTypeProvider;
import dev.springdrop.kernel.schema.ColumnType;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The menu links people add through the admin UI, as a content entity type. A
 * module's own links are declared in code and never stored; these are content,
 * so they are created, edited, and deleted like anything else a site writes. The
 * entity's label carries the link title.
 */
@Component
public class MenuLinkContentEntityType implements EntityTypeProvider {

    public static final String ID = "menu_link_content";

    /** The id of the menu this link hangs in. */
    public static final String MENU = "menu";

    public static final String LINK_URL = "link_url";

    /** The id of the link this one hangs under, or null at the top of the menu. */
    public static final String PARENT = "parent";

    public static final String WEIGHT = "weight";

    public static final String EXPANDED = "expanded";

    public static final String DESCRIPTION = "description";

    public static EntityType definition() {
        return EntityType.content(ID, MenuLink.class)
                .withBaseFields(List.of(
                        BaseFieldDefinition.required(MENU, ColumnType.VARCHAR),
                        BaseFieldDefinition.required(LINK_URL, ColumnType.VARCHAR),
                        BaseFieldDefinition.optional(PARENT, ColumnType.VARCHAR),
                        BaseFieldDefinition.optional(WEIGHT, ColumnType.INTEGER),
                        BaseFieldDefinition.optional(EXPANDED, ColumnType.BOOLEAN),
                        BaseFieldDefinition.optional(DESCRIPTION, ColumnType.VARCHAR),
                        BaseFieldDefinition.optional(BaseFieldDefinition.STATUS, ColumnType.BOOLEAN)));
    }

    @Override
    public List<EntityType> entityTypes() {
        return List.of(definition());
    }
}
