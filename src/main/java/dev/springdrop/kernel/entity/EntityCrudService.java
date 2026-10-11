package dev.springdrop.kernel.entity;

import dev.springdrop.kernel.event.EntityEvent;
import dev.springdrop.kernel.tx.TransactionRunner;
import dev.springdrop.kernel.validation.ConstraintSpec;
import dev.springdrop.kernel.validation.ConstraintViolation;
import dev.springdrop.kernel.validation.ValueLookup;
import dev.springdrop.kernel.validation.Validator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Creates, reads, updates, and deletes content entities. A save validates first
 * and then writes the base row, the revision row, and every field table inside
 * one transaction, so an entity is never half written. Each step publishes the
 * lifecycle event listeners subscribe to.
 */
@Component
public class EntityCrudService {

    private final EntityTypeManager entityTypeManager;
    private final BundleFieldMap bundleFieldMap;
    private final FieldTableStorage fieldTableStorage;
    private final Validator validator;
    private final ValueLookup valueLookup;
    private final ContentEntityStorage contentStorage;
    private final TransactionRunner transactionRunner;
    private final ApplicationEventPublisher events;
    private final List<EntityConstraintProvider> constraintProviders;
    private final DSLContext dsl;
    private final TranslatableFields translatableFields;

    public EntityCrudService(
            EntityTypeManager entityTypeManager,
            BundleFieldMap bundleFieldMap,
            FieldTableStorage fieldTableStorage,
            Validator validator,
            ValueLookup valueLookup,
            ContentEntityStorage contentStorage,
            TransactionRunner transactionRunner,
            ApplicationEventPublisher events,
            List<EntityConstraintProvider> constraintProviders,
            DSLContext dsl,
            TranslatableFields translatableFields) {
        this.entityTypeManager = entityTypeManager;
        this.bundleFieldMap = bundleFieldMap;
        this.fieldTableStorage = fieldTableStorage;
        this.validator = validator;
        this.valueLookup = valueLookup;
        this.contentStorage = contentStorage;
        this.transactionRunner = transactionRunner;
        this.events = events;
        this.constraintProviders = constraintProviders;
        this.dsl = dsl;
        this.translatableFields = translatableFields;
    }

    public EntityData save(EntityData entity) {
        return save(entity, true);
    }

    /**
     * Saves the entity. Of a revisionable type, an entity already stored either
     * gets a new revision or has its current one written over, as asked, and a
     * new entity always gets its first revision.
     *
     * <p>A stored entity of a translatable type saved in a language other than
     * its own is saved as a translation: its label and translated fields are
     * kept for that language, while its base fields and shared fields change
     * for every language, and its own label stays as it was.
     */
    public EntityData save(EntityData entity, boolean newRevision) {
        EntityType type = entityTypeManager.require(entity.entityType());
        validate(type, entity);

        return transactionRunner.call(() -> {
            EntityStorage storage = entityTypeManager.storageFor(type.id());
            EntityData identified = (entity.id() == null) ? entity.withId(storage.nextId(type)) : entity;
            Optional<Map<String, Object>> existing = storage.load(type, identified.id());
            boolean exists = existing.isPresent();
            String original = (type.translatable() && exists)
                    ? (String) existing.get().get(type.keys().langcode()) : identified.langcode();
            boolean translation = !identified.langcode().equals(original);

            EntityData resolved = withUuid(identified, resolveUuid(type, identified, existing));
            EntityData saved = exists ? resolved : withDefaults(type, resolved);
            events.publishEvent(new EntityEvent(saved, type.id(), EntityEvent.Phase.PRESAVE));

            Long currentRevision = type.revisionable()
                    ? existing.map(row -> asLong(row.get(type.keys().revision()))).orElse(null)
                    : null;
            boolean overwrite = !newRevision && currentRevision != null;
            Long revisionId = !type.revisionable() ? null : (overwrite ? currentRevision : nextRevisionId(type));
            EntityData own = translation ? new EntityData(saved.entityType(), saved.id(), saved.uuid(),
                    saved.bundle(), (String) existing.get().get(type.keys().label()), original, null,
                    saved.fields()) : saved;
            storage.save(type, saved.id(), baseValues(type, own, revisionId));
            if (type.revisionable()) {
                if (overwrite) {
                    deleteRevisionRow(type, revisionId);
                }
                writeRevision(type, own, revisionId);
            }
            writeFields(type, saved, revisionId, original);
            if (translation) {
                contentStorage.saveTranslation(type, saved.id(), saved.langcode(), saved.label());
            }

            EntityData result = new EntityData(saved.entityType(), saved.id(), saved.uuid(), saved.bundle(),
                    saved.label(), saved.langcode(), revisionId, saved.fields());
            events.publishEvent(new EntityEvent(
                    result, type.id(), exists ? EntityEvent.Phase.UPDATE : EntityEvent.Phase.INSERT));
            return result;
        });
    }

    public Optional<EntityData> load(String entityTypeId, Object id) {
        return load(entityTypeId, id, EntityData.DEFAULT_LANGCODE, null);
    }

    /**
     * The entity in one language and revision. A null revision id reads the
     * revision the base row points at, the one the site shows. An entity of a
     * translatable type not translated into the language is read in its own.
     */
    public Optional<EntityData> load(String entityTypeId, Object id, String langcode, Long revisionId) {
        EntityType type = entityTypeManager.require(entityTypeId);
        return entityTypeManager.storageFor(type.id()).load(type, id).map(row -> {
            EntityData entity = assemble(type, id, row, langcode, revisionId);
            events.publishEvent(new EntityEvent(entity, type.id(), EntityEvent.Phase.LOAD));
            return entity;
        });
    }

    /** The languages the entity is in: its own first, then those it is translated into, by code. */
    public List<String> translations(String entityTypeId, Object id) {
        EntityType type = entityTypeManager.require(entityTypeId);
        return entityTypeManager.storageFor(type.id()).load(type, id).map(row -> {
            List<String> langcodes = new ArrayList<>();
            langcodes.add(type.translatable() ? (String) row.get(type.keys().langcode())
                    : EntityData.DEFAULT_LANGCODE);
            if (type.translatable()) {
                langcodes.addAll(contentStorage.translationLangcodes(type, id));
            }
            return List.copyOf(langcodes);
        }).orElse(List.of());
    }

    /**
     * Removes one translation: its label and the values of its translated
     * fields. An entity's own language is not a translation and cannot be
     * removed this way.
     */
    public void deleteTranslation(String entityTypeId, Object id, String langcode) {
        EntityType type = entityTypeManager.require(entityTypeId);
        transactionRunner.run(() -> {
            Map<String, Object> row = entityTypeManager.storageFor(type.id()).load(type, id).orElseThrow();
            if (!type.translatable() || langcode.equals(row.get(type.keys().langcode()))) {
                throw new IllegalArgumentException(entityTypeId + " " + id + " has no translation " + langcode
                        + " to delete");
            }
            EntityData entity = assemble(type, id, row, (String) row.get(type.keys().langcode()), null);
            List<String> translated = fieldsOf(type, entity.bundle()).stream()
                    .filter(translatableFields.of(type.id())::contains).toList();
            fieldTableStorage.deleteLanguage(type, id, langcode, translated);
            contentStorage.deleteTranslation(type, id, langcode);
            events.publishEvent(new EntityEvent(entity, type.id(), EntityEvent.Phase.UPDATE));
        });
    }

    public void delete(String entityTypeId, Object id) {
        EntityType type = entityTypeManager.require(entityTypeId);
        transactionRunner.run(() -> {
            EntityStorage storage = entityTypeManager.storageFor(type.id());
            storage.load(type, id).ifPresent(row -> {
                EntityData entity = assemble(type, id, row, EntityData.DEFAULT_LANGCODE, null);
                events.publishEvent(new EntityEvent(entity, type.id(), EntityEvent.Phase.DELETE));
                fieldTableStorage.deleteAll(type, id, fieldsOf(type, entity.bundle()));
                storage.delete(type, id);
            });
        });
    }

    /**
     * Saves a new revision of a stored entity without making it the one the
     * site shows: the revision is written beside the others, while the entity's
     * own row and current field values stay as they were. A later default save
     * makes a revision current again.
     */
    public EntityData saveForwardRevision(EntityData entity) {
        EntityType type = entityTypeManager.require(entity.entityType());
        if (!type.revisionable() || entity.id() == null) {
            throw new IllegalArgumentException("Only a stored entity of a revisionable type has forward revisions");
        }
        validate(type, entity);

        return transactionRunner.call(() -> {
            Map<String, Object> existing = entityTypeManager.storageFor(type.id()).load(type, entity.id())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No " + type.id() + " " + entity.id() + " to add a revision to"));
            EntityData saved = withUuid(entity, (UUID) existing.get(type.keys().uuid()));
            events.publishEvent(new EntityEvent(saved, type.id(), EntityEvent.Phase.PRESAVE));

            Long revisionId = nextRevisionId(type);
            writeRevision(type, saved, revisionId);
            for (String field : fieldsOf(type, saved.bundle())) {
                if (saved.fields().containsKey(field)) {
                    fieldTableStorage.writeRevision(
                            type, saved.id(), revisionId, saved.langcode(), field, saved.fields().get(field));
                }
            }

            EntityData result = new EntityData(saved.entityType(), saved.id(), saved.uuid(), saved.bundle(),
                    saved.label(), saved.langcode(), revisionId, saved.fields());
            events.publishEvent(new EntityEvent(result, type.id(), EntityEvent.Phase.UPDATE));
            return result;
        });
    }

    /** The newest revision the entity has, which may be ahead of the one the site shows. */
    public Optional<Long> latestRevisionId(String entityTypeId, Object id) {
        EntityType type = entityTypeManager.require(entityTypeId);
        return Optional.ofNullable(dsl.select(DSL.max(DSL.field(DSL.name(type.keys().revision()), Long.class)))
                .from(DSL.table(DSL.name(type.revisionTable())))
                .where(DSL.field(DSL.name(type.keys().id())).eq(id))
                .fetchOne(0, Long.class));
    }

    /** Every revision the entity has, newest first, as each revision's row holds it. */
    public List<EntityRevision> revisions(String entityTypeId, Object id) {
        EntityType type = entityTypeManager.require(entityTypeId);
        return dsl.select()
                .from(DSL.table(DSL.name(type.revisionTable())))
                .where(DSL.field(DSL.name(type.keys().id())).eq(id))
                .orderBy(DSL.field(DSL.name(type.keys().revision())).desc())
                .fetch()
                .map(record -> {
                    Map<String, Object> row = record.intoMap();
                    Map<String, Object> values = new LinkedHashMap<>();
                    for (BaseFieldDefinition baseField : type.baseFields()) {
                        Object stored = row.get(baseField.name());
                        if (stored != null) {
                            values.put(baseField.name(), contentStorage.javaValue(type, baseField.name(), stored));
                        }
                    }
                    return new EntityRevision(asLong(row.get(type.keys().revision())),
                            (String) row.get(type.keys().label()), values);
                });
    }

    /**
     * Removes one revision and the field values it held. The revision the site
     * shows cannot be removed, since the entity would have none to show.
     */
    public void deleteRevision(String entityTypeId, Object id, long revisionId) {
        EntityType type = entityTypeManager.require(entityTypeId);
        transactionRunner.run(() -> {
            Map<String, Object> row = entityTypeManager.storageFor(type.id()).load(type, id).orElseThrow();
            if (Long.valueOf(revisionId).equals(asLong(row.get(type.keys().revision())))) {
                throw new IllegalArgumentException("The current revision of " + entityTypeId + " " + id
                        + " cannot be deleted");
            }
            String bundle = (type.keys().bundle() == null) ? null : (String) row.get(type.keys().bundle());
            fieldTableStorage.deleteRevision(type, id, revisionId, fieldsOf(type, bundle));
            deleteRevisionRow(type, revisionId);
        });
    }

    private void deleteRevisionRow(EntityType type, Long revisionId) {
        dsl.deleteFrom(DSL.table(DSL.name(type.revisionTable())))
                .where(DSL.field(DSL.name(type.keys().revision())).eq(revisionId))
                .execute();
    }

    private void validate(EntityType type, EntityData entity) {
        List<ConstraintSpec> specs = new ArrayList<>();
        for (EntityConstraintProvider provider : constraintProviders) {
            specs.addAll(provider.constraintsFor(type, entity.bundle()));
        }
        List<ConstraintViolation> violations = validator.validate(entity, specs, valueLookup);
        if (!violations.isEmpty()) {
            throw new EntityValidationException(type.id(), violations);
        }
    }

    private EntityData assemble(EntityType type, Object id, Map<String, Object> row, String langcode,
            Long revisionId) {

        String bundle = (type.keys().bundle() == null) ? null : (String) row.get(type.keys().bundle());
        Long storedRevision = type.revisionable() ? asLong(row.get(type.keys().revision())) : null;
        Long readRevision = (revisionId != null) ? revisionId : storedRevision;
        String original = type.translatable() ? (String) row.get(type.keys().langcode()) : langcode;
        Optional<String> translationLabel = langcode.equals(original) ? Optional.empty()
                : contentStorage.translationLabel(type, id, langcode);
        String readLangcode = translationLabel.isPresent() ? langcode : original;

        List<String> fields = fieldsOf(type, bundle);
        Set<String> translated = translatableFields.of(type.id());
        List<String> ownFields = fields.stream().filter(translated::contains).toList();
        List<String> sharedFields = fields.stream().filter(field -> !translated.contains(field)).toList();
        boolean earlierRevision = revisionId != null && !revisionId.equals(storedRevision);
        Map<String, Object> values = new LinkedHashMap<>(earlierRevision
                ? fieldTableStorage.readRevision(type, id, revisionId, original, sharedFields)
                : fieldTableStorage.read(type, id, original, sharedFields));
        values.putAll(earlierRevision
                ? fieldTableStorage.readRevision(type, id, revisionId, readLangcode, ownFields)
                : fieldTableStorage.read(type, id, readLangcode, ownFields));
        Map<String, Object> revisionRow = earlierRevision ? revisionRow(type, id, revisionId).orElse(row) : row;
        for (BaseFieldDefinition baseField : type.baseFields()) {
            Object stored = revisionRow.get(baseField.name());
            if (stored != null) {
                values.put(baseField.name(), contentStorage.javaValue(type, baseField.name(), stored));
            }
        }

        return new EntityData(
                type.id(),
                id,
                (UUID) row.get(type.keys().uuid()),
                bundle,
                translationLabel.orElse((String) revisionRow.get(type.keys().label())),
                readLangcode,
                readRevision,
                values);
    }

    /**
     * The row an earlier revision wrote, holding the title and base field values
     * the entity had then. A revision the entity never had reads as the current one.
     */
    private Optional<Map<String, Object>> revisionRow(EntityType type, Object id, Long revisionId) {
        return dsl.select()
                .from(DSL.table(DSL.name(type.revisionTable())))
                .where(DSL.field(DSL.name(type.keys().revision())).eq(revisionId)
                        .and(DSL.field(DSL.name(type.keys().id())).eq(id)))
                .fetchOptional()
                .map(found -> found.intoMap());
    }

    private Map<String, Object> baseValues(EntityType type, EntityData entity, Long revisionId) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put(type.keys().uuid(), entity.uuid());
        values.put(type.keys().label(), entity.label());
        if (type.keys().bundle() != null) {
            values.put(type.keys().bundle(), entity.bundle());
        }
        if (type.translatable()) {
            values.put(type.keys().langcode(), entity.langcode());
        }
        if (type.revisionable()) {
            values.put(type.keys().revision(), revisionId);
        }
        values.putAll(baseFieldValues(type, entity));
        return values;
    }

    /** The values of the type's code-defined fields, which live in its own tables. */
    private static Map<String, Object> baseFieldValues(EntityType type, EntityData entity) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (BaseFieldDefinition baseField : type.baseFields()) {
            if (entity.fields().containsKey(baseField.name())) {
                values.put(baseField.name(), entity.fields().get(baseField.name()));
            }
        }
        return values;
    }


    private void writeRevision(EntityType type, EntityData entity, Long revisionId) {
        Map<org.jooq.Field<?>, Object> row = new LinkedHashMap<>();
        row.put(DSL.field(DSL.name(type.keys().revision())), revisionId);
        row.put(DSL.field(DSL.name(type.keys().id())), entity.id());
        row.put(DSL.field(DSL.name(type.keys().label())), entity.label());
        if (type.translatable()) {
            row.put(DSL.field(DSL.name(type.keys().langcode())), entity.langcode());
        }
        baseFieldValues(type, entity).forEach((name, value) ->
                row.put(DSL.field(DSL.name(name)), contentStorage.columnValue(type, name, value)));
        dsl.insertInto(DSL.table(DSL.name(type.revisionTable()))).set(row).execute();
    }

    /** Writes each field's values, a translated field's in the entity's language and a shared one's in its own. */
    private void writeFields(EntityType type, EntityData entity, Long revisionId, String original) {
        Set<String> translated = translatableFields.of(type.id());
        for (String field : fieldsOf(type, entity.bundle())) {
            if (entity.fields().containsKey(field)) {
                fieldTableStorage.write(type, entity.id(), revisionId,
                        translated.contains(field) ? entity.langcode() : original, field, entity.fields().get(field));
            }
        }
    }

    /**
     * A type without bundles hangs its fields on its own name, so an unbundled
     * type is still fieldable and every one of its entities carries the same set.
     */
    private List<String> fieldsOf(EntityType type, String bundle) {
        return bundleFieldMap.fieldNames(
                type.id(), (type.bundleEntityType() == null) ? type.id() : bundle);
    }

    private Long nextRevisionId(EntityType type) {
        Long highest = dsl.select(DSL.max(DSL.field(DSL.name(type.keys().revision()), Long.class)))
                .from(DSL.table(DSL.name(type.revisionTable())))
                .fetchOne(0, Long.class);
        return (highest == null) ? 1L : highest + 1;
    }

    /**
     * The uuid the entity keeps: the one it carries, else the one already
     * stored, else a fresh one for an entity being created.
     */
    private static UUID resolveUuid(EntityType type, EntityData entity, Optional<Map<String, Object>> existing) {
        if (entity.uuid() != null) {
            return entity.uuid();
        }
        return existing.map(row -> (UUID) row.get(type.keys().uuid())).orElseGet(UUID::randomUUID);
    }

    /** The entity with each base field it leaves out given the field's default, where it has one. */
    private static EntityData withDefaults(EntityType type, EntityData entity) {
        Map<String, Object> fields = new LinkedHashMap<>(entity.fields());
        for (BaseFieldDefinition baseField : type.baseFields()) {
            if (baseField.defaultValue() != null) {
                fields.putIfAbsent(baseField.name(), baseField.defaultValue());
            }
        }
        return entity.withFields(fields);
    }

    private static EntityData withUuid(EntityData entity, UUID uuid) {
        return new EntityData(entity.entityType(), entity.id(), uuid, entity.bundle(),
                entity.label(), entity.langcode(), entity.revisionId(), entity.fields());
    }

    private static Long asLong(Object value) {
        return (value == null) ? null : ((Number) value).longValue();
    }
}
