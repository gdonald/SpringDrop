package dev.springdrop.kernel.node;

import dev.springdrop.kernel.block.BlockContext;
import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.field.display.ViewDisplayManager;
import dev.springdrop.kernel.layout.LayoutDisplayManager;
import dev.springdrop.kernel.path.PathAliasManager;
import dev.springdrop.kernel.render.Renderable;
import dev.springdrop.kernel.theme.ThemeService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** The site's nodes: creating, storing, and drawing them. */
@Component
public class NodeService {

    public static final String TEMPLATE_DIRECTORY = "node";

    /** The tag every listing of nodes carries, invalidated when any node changes. */
    public static final String LIST_CACHE_TAG = "node_list";

    private final EntityCrudService entities;
    private final EntityQueryExecutor queries;
    private final EntityTypeManager entityTypeManager;
    private final ViewDisplayManager displays;
    private final LayoutDisplayManager layouts;
    private final ThemeService themes;
    private final Clock clock;
    private final PathAliasManager aliases;

    public NodeService(
            EntityCrudService entities,
            EntityQueryExecutor queries,
            EntityTypeManager entityTypeManager,
            ViewDisplayManager displays,
            LayoutDisplayManager layouts,
            ThemeService themes,
            Clock clock,
            PathAliasManager aliases) {
        this.entities = entities;
        this.queries = queries;
        this.entityTypeManager = entityTypeManager;
        this.displays = displays;
        this.layouts = layouts;
        this.themes = themes;
        this.clock = clock;
        this.aliases = aliases;
    }

    /**
     * Creates the tables nodes are stored in. Nodes are the site's content, so
     * this runs as the application starts, and creating tables that exist
     * leaves them as they are.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void install() {
        entityTypeManager.installStorage(NodeEntityType.ID);
    }

    /** The tag invalidating what one node rendered. */
    public static String cacheTag(Object id) {
        return NodeEntityType.ID + ":" + id;
    }

    /** Whether any node is of this type, which keeps the type from being deleted. */
    public boolean inUse(String typeId) {
        return queries.query(NodeEntityType.ID)
                .condition(Condition.equal(NodeEntityType.BUNDLE_KEY, typeId))
                .count() > 0;
    }

    /** A node not yet saved, starting out the way its type says and owned by the given account. */
    public EntityData create(NodeType type, long ownerId) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(BaseFieldDefinition.STATUS, type.published());
        fields.put(NodeEntityType.PROMOTE, type.promoted());
        fields.put(NodeEntityType.STICKY, type.sticky());
        fields.put(BaseFieldDefinition.OWNER, ownerId);
        return EntityData.of(NodeEntityType.ID, null, type.id(), "", fields);
    }

    /** Stores a node as a new revision saved by the given account, with no log message. */
    public EntityData save(EntityData node, long accountId) {
        return save(node, accountId, true, "");
    }

    /**
     * Stores a node as saved by the given account, either as a new revision or
     * over its current one, with the log message the account wrote about it. A
     * node being created is stamped with when it was created, every save with
     * when it was changed, and every new revision with when it was saved. The
     * revision saved is the one the site shows.
     */
    public EntityData save(EntityData node, long accountId, boolean newRevision, String log) {
        return entities.save(stamped(node, accountId, log, newRevision, true), newRevision);
    }

    /**
     * Stores a node as a new revision the site does not show yet, a forward
     * revision ahead of the one it shows. The node itself is left as it was.
     */
    public EntityData saveForwardRevision(EntityData node, long accountId, String log) {
        return entities.saveForwardRevision(stamped(node, accountId, log, true, false));
    }

    /** The newest revision of the node, which may be ahead of the one the site shows. */
    public EntityData latestRevision(EntityData node) {
        long latest = entities.latestRevisionId(NodeEntityType.ID, node.id()).orElseThrow();
        return entities.load(NodeEntityType.ID, node.id(), node.langcode(), latest).orElseThrow();
    }

    private EntityData stamped(EntityData node, long accountId, String log, boolean newRevision,
            boolean defaultRevision) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Map<String, Object> fields = new LinkedHashMap<>(node.fields());
        fields.putIfAbsent(BaseFieldDefinition.CREATED, now);
        fields.put(BaseFieldDefinition.CHANGED, now);
        fields.put(NodeEntityType.REVISION_USER, accountId);
        fields.put(NodeEntityType.REVISION_LOG, log);
        fields.put(NodeEntityType.REVISION_DEFAULT, defaultRevision);
        if (newRevision) {
            fields.put(NodeEntityType.REVISION_CREATED, now);
        } else {
            fields.putIfAbsent(NodeEntityType.REVISION_CREATED, now);
        }
        return node.withFields(fields);
    }

    public Optional<EntityData> find(long id) {
        return entities.load(NodeEntityType.ID, id);
    }

    /** Deletes the node and the aliases of its page. */
    public void delete(long id) {
        entities.delete(NodeEntityType.ID, id);
        aliases.deleteAll(NodeEntityType.path(id));
    }

    /**
     * The node drawn in a view mode through its themed template. Its fields are
     * laid out by the content type's layout for that mode when Layout Builder
     * draws it, and by the type's display otherwise. On its own page the page
     * title already names the node, so the template leaves its title off.
     */
    public Renderable build(EntityData node, String viewMode, boolean page) {
        BlockContext context = BlockContext.of(NodeEntityType.path(node.id()), node.label());
        Renderable content = layouts.render(node, viewMode, context).orElseGet(() -> Renderable.of("markup")
                .with("value", displays.render(node, viewMode)));

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("id", node.id());
        variables.put("label", node.label());
        variables.put("url", aliases.outbound(NodeEntityType.path(node.id())));
        variables.put("viewMode", viewMode);
        variables.put("page", page);
        variables.put("published", Boolean.TRUE.equals(node.fields().get(BaseFieldDefinition.STATUS)));
        return themes.entity(TEMPLATE_DIRECTORY, NodeEntityType.ID, node.bundle(), viewMode,
                        String.valueOf(node.id()), variables)
                .child(content)
                .cacheTag(cacheTag(node.id()));
    }
}
