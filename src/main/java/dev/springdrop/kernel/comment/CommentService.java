package dev.springdrop.kernel.comment;

import dev.springdrop.kernel.entity.BaseFieldDefinition;
import dev.springdrop.kernel.entity.EntityCrudService;
import dev.springdrop.kernel.entity.EntityData;
import dev.springdrop.kernel.entity.EntityTypeManager;
import dev.springdrop.kernel.entity.query.Condition;
import dev.springdrop.kernel.entity.query.EntityQueryExecutor;
import dev.springdrop.kernel.entity.query.Sort;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * The comments posted about entities, and the threads they form. Each comment
 * gets a thread position as it is posted: a top-level comment the next one
 * after the entity's last, and a reply the next one under the comment it
 * replies to. Sorting by that position gives the threaded order, replies under
 * what they reply to.
 */
@Component
public class CommentService {

    /** How long a subject made from a comment's text is. */
    static final int SUBJECT_LENGTH = 29;

    private static final int RADIX = 36;

    private final EntityCrudService entities;
    private final EntityQueryExecutor queries;
    private final EntityTypeManager entityTypeManager;
    private final Clock clock;

    public CommentService(
            EntityCrudService entities, EntityQueryExecutor queries, EntityTypeManager entityTypeManager,
            Clock clock) {
        this.entities = entities;
        this.queries = queries;
        this.entityTypeManager = entityTypeManager;
        this.clock = clock;
    }

    /**
     * Creates the tables comments are stored in. Comments are part of the
     * site's content model, so this runs as the application starts, and creating
     * tables that exist leaves them as they are.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void install() {
        entityTypeManager.installStorage(CommentEntityType.ID);
    }

    /** The tag every listing of comments carries, invalidated when any comment changes. */
    public static final String LIST_CACHE_TAG = "comment_list";

    /** A comment not yet saved, about one entity through one of its comment fields. */
    public static EntityData draft(String hostType, long hostId, String field, long parent) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put(CommentEntityType.HOST_TYPE, hostType);
        fields.put(CommentEntityType.HOST_ID, hostId);
        fields.put(CommentEntityType.FIELD_NAME, field);
        fields.put(CommentEntityType.PARENT, parent);
        return EntityData.of(CommentEntityType.ID, null, null, "", fields);
    }

    /**
     * Saves a new comment, giving it its place in the thread and when it was
     * posted. A comment with no subject is given the start of its text.
     */
    public EntityData post(EntityData comment) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Map<String, Object> fields = new LinkedHashMap<>(comment.fields());
        fields.put(CommentEntityType.THREAD, nextThread(comment));
        fields.put(BaseFieldDefinition.CREATED, now);
        fields.put(BaseFieldDefinition.CHANGED, now);
        String subject = comment.label().isBlank() ? subjectFrom(textOf(comment)) : comment.label();
        return entities.save(new EntityData(CommentEntityType.ID, null, null, null, subject,
                comment.langcode(), null, fields));
    }

    /** Saves a change to a comment that exists, stamped with when it changed. */
    public EntityData update(EntityData comment) {
        Map<String, Object> fields = new LinkedHashMap<>(comment.fields());
        fields.put(BaseFieldDefinition.CHANGED, OffsetDateTime.now(clock));
        String subject = comment.label().isBlank() ? subjectFrom(textOf(comment)) : comment.label();
        return entities.save(new EntityData(CommentEntityType.ID, comment.id(), comment.uuid(), null, subject,
                comment.langcode(), null, fields));
    }

    public Optional<EntityData> find(long id) {
        return entities.load(CommentEntityType.ID, id);
    }

    /** Publishes a comment that was waiting for approval. */
    public void approve(long id) {
        find(id).ifPresent(comment -> {
            Map<String, Object> fields = new LinkedHashMap<>(comment.fields());
            fields.put(BaseFieldDefinition.STATUS, true);
            entities.save(comment.withFields(fields));
        });
    }

    /** Deletes a comment along with every reply below it. */
    public void delete(long id) {
        for (Object reply : queries.query(CommentEntityType.ID)
                .condition(Condition.equal(CommentEntityType.PARENT, id))
                .ids()) {
            delete(((Number) reply).longValue());
        }
        entities.delete(CommentEntityType.ID, id);
    }

    /**
     * The comments posted about an entity through one field, threaded or in the
     * order posted. Comments waiting for approval are left out unless asked for.
     */
    public List<CommentThreadItem> thread(String hostType, long hostId, String field, boolean threaded,
            boolean withUnpublished) {
        List<EntityData> comments = new ArrayList<>();
        for (Object id : queries.query(CommentEntityType.ID)
                .condition(Condition.equal(CommentEntityType.HOST_TYPE, hostType))
                .condition(Condition.equal(CommentEntityType.HOST_ID, hostId))
                .condition(Condition.equal(CommentEntityType.FIELD_NAME, field))
                .sort(Sort.ascending("id"))
                .ids()) {
            find(((Number) id).longValue())
                    .filter(comment -> withUnpublished || published(comment))
                    .ifPresent(comments::add);
        }
        if (threaded) {
            comments.sort(Comparator.comparing(comment -> sortKey(threadOf(comment))));
        }
        return comments.stream()
                .map(comment -> new CommentThreadItem(comment, threaded ? depthOf(threadOf(comment)) : 0))
                .toList();
    }

    /** Comments waiting for approval, oldest first. */
    public List<EntityData> unapproved() {
        List<EntityData> comments = new ArrayList<>();
        for (Object id : queries.query(CommentEntityType.ID)
                .condition(Condition.equal(BaseFieldDefinition.STATUS, false))
                .sort(Sort.ascending("id"))
                .ids()) {
            find(((Number) id).longValue()).ifPresent(comments::add);
        }
        return comments;
    }

    /** Published comments, newest first. */
    public List<EntityData> published() {
        List<EntityData> comments = new ArrayList<>();
        for (Object id : queries.query(CommentEntityType.ID)
                .condition(Condition.equal(BaseFieldDefinition.STATUS, true))
                .sort(Sort.descending("id"))
                .ids()) {
            find(((Number) id).longValue()).ifPresent(comments::add);
        }
        return comments;
    }

    public static boolean published(EntityData comment) {
        return Boolean.TRUE.equals(comment.fields().get(BaseFieldDefinition.STATUS));
    }

    public static String threadOf(EntityData comment) {
        return String.valueOf(comment.fields().getOrDefault(CommentEntityType.THREAD, ""));
    }

    /** How many levels below the top of its thread a comment sits. */
    public static int depthOf(String thread) {
        return (int) thread.chars().filter(character -> character == '.').count();
    }

    /** Whether a comment was posted about this entity through this field. */
    public static boolean belongsTo(EntityData comment, String hostType, long hostId, String field) {
        return List.of(hostType, String.valueOf(hostId), field).equals(List.of(
                String.valueOf(comment.fields().get(CommentEntityType.HOST_TYPE)),
                String.valueOf(comment.fields().get(CommentEntityType.HOST_ID)),
                String.valueOf(comment.fields().get(CommentEntityType.FIELD_NAME))));
    }

    public static String textOf(EntityData comment) {
        return String.valueOf(comment.fields().getOrDefault(CommentEntityType.BODY, ""));
    }

    /**
     * The next place in the thread: after the entity's last top-level comment,
     * or after the last reply under the comment being replied to.
     */
    private String nextThread(EntityData comment) {
        long parent = ((Number) comment.fields().getOrDefault(CommentEntityType.PARENT, CommentEntityType.NO_PARENT))
                .longValue();
        String prefix = (parent == CommentEntityType.NO_PARENT) ? ""
                : find(parent).map(CommentService::threadOf)
                        .map(thread -> thread.substring(0, thread.length() - 1) + ".")
                        .orElse("");
        int last = -1;
        for (Object id : queries.query(CommentEntityType.ID)
                .condition(Condition.equal(CommentEntityType.HOST_TYPE, comment.fields().get(CommentEntityType.HOST_TYPE)))
                .condition(Condition.equal(CommentEntityType.HOST_ID, comment.fields().get(CommentEntityType.HOST_ID)))
                .condition(Condition.equal(CommentEntityType.PARENT, parent))
                .ids()) {
            String sibling = sortKey(find(((Number) id).longValue()).map(CommentService::threadOf).orElseThrow());
            last = Math.max(last, fromVancode(sibling.substring(sibling.lastIndexOf('.') + 1)));
        }
        return prefix + toVancode(last + 1) + "/";
    }

    /** A number as a sortable code: its base-36 digits, led by one less than how many there are. */
    static String toVancode(int number) {
        String digits = Integer.toString(number, RADIX);
        return (digits.length() - 1) + digits;
    }

    static int fromVancode(String code) {
        return Integer.parseInt(code.substring(1), RADIX);
    }

    /** The thread position without its closing slash, so a comment sorts before its replies. */
    private static String sortKey(String thread) {
        return thread.replaceFirst("/$", "");
    }

    /** The start of the text, cut at the last whole word that fits. */
    static String subjectFrom(String text) {
        String trimmed = text.strip();
        if (trimmed.length() <= SUBJECT_LENGTH) {
            return trimmed;
        }
        String cut = trimmed.substring(0, SUBJECT_LENGTH);
        int lastSpace = cut.lastIndexOf(' ');
        boolean midWord = !Character.isWhitespace(trimmed.charAt(SUBJECT_LENGTH));
        return (midWord && lastSpace > 0 ? cut.substring(0, lastSpace) : cut).strip();
    }
}
