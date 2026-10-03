package dev.springdrop.kernel.taxonomy;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The terms of the site's vocabularies and the tree each vocabulary's terms
 * form. A term sits under one parent in its own vocabulary, or at the top, and
 * a vocabulary's tree lists each term's children lightest first and then by
 * name.
 */
@Component
public class TaxonomyService {

    private static final Comparator<EntityData> SIBLING_ORDER = Comparator
            .comparingInt(TaxonomyService::weightOf)
            .thenComparing(EntityData::label)
            .thenComparing(term -> idOf(term));

    private final EntityCrudService entities;
    private final EntityQueryExecutor queries;
    private final EntityTypeManager entityTypeManager;
    private final Clock clock;

    public TaxonomyService(
            EntityCrudService entities,
            EntityQueryExecutor queries,
            EntityTypeManager entityTypeManager,
            Clock clock) {
        this.entities = entities;
        this.queries = queries;
        this.entityTypeManager = entityTypeManager;
        this.clock = clock;
    }

    /**
     * Creates the tables terms are stored in. Taxonomy is part of the site's
     * content model, so this runs as the application starts, and creating
     * tables that exist leaves them as they are.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void install() {
        entityTypeManager.installStorage(TaxonomyEntityType.ID);
    }

    /** The tag invalidating what one term rendered. */
    public static String cacheTag(Object id) {
        return TaxonomyEntityType.ID + ":" + id;
    }

    /** A term not yet saved: published, at the top of its vocabulary, and as light as the rest. */
    public EntityData create(String vocabulary) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(BaseFieldDefinition.STATUS, true);
        fields.put(TaxonomyEntityType.WEIGHT, 0);
        fields.put(TaxonomyEntityType.PARENT, TaxonomyEntityType.ROOT);
        fields.put(TaxonomyEntityType.DESCRIPTION, "");
        return EntityData.of(TaxonomyEntityType.ID, null, vocabulary, "", fields);
    }

    /**
     * Stores a term as a new revision, stamped with when it changed. Its parent
     * has to be the top of the tree or a term of the same vocabulary that is
     * neither the term itself nor below it.
     */
    public EntityData save(EntityData term) {
        long parent = parentOf(term);
        if (parent != TaxonomyEntityType.ROOT) {
            EntityData parentTerm = find(parent).orElseThrow(() -> new TermHierarchyException(
                    "The parent term " + parent + " does not exist."));
            if (!parentTerm.bundle().equals(term.bundle())) {
                throw new TermHierarchyException("The parent term is in another vocabulary.");
            }
            if (term.id() != null && (parent == idOf(term) || descendantsOf(idOf(term)).contains(parent))) {
                throw new TermHierarchyException("A term cannot sit under itself or a term below it.");
            }
        }
        Map<String, Object> fields = new LinkedHashMap<>(term.fields());
        fields.put(BaseFieldDefinition.CHANGED, OffsetDateTime.now(clock));
        return entities.save(term.withFields(fields));
    }

    public Optional<EntityData> find(long id) {
        return entities.load(TaxonomyEntityType.ID, id);
    }

    /** Deletes a term along with every term below it. */
    public void delete(long id) {
        for (long descendant : descendantsOf(id)) {
            entities.delete(TaxonomyEntityType.ID, descendant);
        }
        entities.delete(TaxonomyEntityType.ID, id);
    }

    /** Every term of a vocabulary, in no particular order. */
    public List<EntityData> termsIn(String vocabulary) {
        List<EntityData> terms = new ArrayList<>();
        for (Object id : queries.query(TaxonomyEntityType.ID)
                .condition(Condition.equal(TaxonomyEntityType.BUNDLE_KEY, vocabulary))
                .ids()) {
            find(((Number) id).longValue()).ifPresent(terms::add);
        }
        return terms;
    }

    /** Deletes every term of a vocabulary, the step deleting the vocabulary takes. */
    public void deleteTermsIn(String vocabulary) {
        termsIn(vocabulary).forEach(term -> entities.delete(TaxonomyEntityType.ID, term.id()));
    }

    /**
     * A vocabulary's terms in tree order: each term followed by the terms under
     * it, siblings lightest first and then by name. A term whose parent is gone
     * is listed at the top.
     */
    public List<TermTreeItem> tree(String vocabulary) {
        List<EntityData> terms = termsIn(vocabulary);
        Set<Long> ids = new HashSet<>();
        terms.forEach(term -> ids.add(idOf(term)));
        Map<Long, List<EntityData>> children = new LinkedHashMap<>();
        for (EntityData term : terms) {
            long parent = ids.contains(parentOf(term)) ? parentOf(term) : TaxonomyEntityType.ROOT;
            children.computeIfAbsent(parent, key -> new ArrayList<>()).add(term);
        }
        List<TermTreeItem> tree = new ArrayList<>();
        addBranch(tree, children, TaxonomyEntityType.ROOT, 0);
        return tree;
    }

    /** The ids of every term below one term. */
    public Set<Long> descendantsOf(long id) {
        Set<Long> descendants = new HashSet<>();
        List<Long> pending = new ArrayList<>(List.of(id));
        while (!pending.isEmpty()) {
            long current = pending.removeLast();
            for (Object child : queries.query(TaxonomyEntityType.ID)
                    .condition(Condition.equal(TaxonomyEntityType.PARENT, current))
                    .ids()) {
                long childId = ((Number) child).longValue();
                if (descendants.add(childId)) {
                    pending.add(childId);
                }
            }
        }
        return descendants;
    }

    private static void addBranch(List<TermTreeItem> tree, Map<Long, List<EntityData>> children, long parent,
            int depth) {
        for (EntityData term : children.getOrDefault(parent, List.of()).stream().sorted(SIBLING_ORDER).toList()) {
            tree.add(new TermTreeItem(idOf(term), term.label(), parent, weightOf(term), depth));
            addBranch(tree, children, idOf(term), depth + 1);
        }
    }

    public static long parentOf(EntityData term) {
        return (term.fields().get(TaxonomyEntityType.PARENT) instanceof Number parent)
                ? parent.longValue() : TaxonomyEntityType.ROOT;
    }

    public static int weightOf(EntityData term) {
        return (term.fields().get(TaxonomyEntityType.WEIGHT) instanceof Number weight) ? weight.intValue() : 0;
    }

    private static long idOf(EntityData term) {
        return ((Number) term.id()).longValue();
    }
}
