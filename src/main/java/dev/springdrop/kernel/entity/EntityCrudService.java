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
            DSLContext dsl) {
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
    }

    public EntityData save(EntityData entity) {
        EntityType type = entityTypeManager.require(entity.entityType());
        validate(type, entity);

        return transactionRunner.call(() -> {
            EntityStorage storage = entityTypeManager.storageFor(type.id());
            EntityData identified = (entity.id() == null) ? entity.withId(storage.nextId(type)) : entity;
            Optional<Map<String, Object>> existing = storage.load(type, identified.id());
            boolean exists = existing.isPresent();

            EntityData saved = withUuid(identified, resolveUuid(type, identified, existing));
            events.publishEvent(new EntityEvent(saved, type.id(), EntityEvent.Phase.PRESAVE));

            Long revisionId = type.revisionable() ? nextRevisionId(type) : null;
            storage.save(type, saved.id(), baseValues(type, saved, revisionId));
            if (type.revisionable()) {
                writeRevision(type, saved, revisionId);
            }
            writeFields(type, saved, revisionId);

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
     * revision the base row points at, the one the site shows.
     */
    public Optional<EntityData> load(String entityTypeId, Object id, String langcode, Long revisionId) {
        EntityType type = entityTypeManager.require(entityTypeId);
        return entityTypeManager.storageFor(type.id()).load(type, id).map(row -> {
            EntityData entity = assemble(type, id, row, langcode, revisionId);
            events.publishEvent(new EntityEvent(entity, type.id(), EntityEvent.Phase.LOAD));
            return entity;
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

        List<String> fields = fieldsOf(type, bundle);
        boolean earlierRevision = revisionId != null && !revisionId.equals(storedRevision);
        Map<String, Object> values = new LinkedHashMap<>(earlierRevision
                ? fieldTableStorage.readRevision(type, id, revisionId, langcode, fields)
                : fieldTableStorage.read(type, id, langcode, fields));
        for (BaseFieldDefinition baseField : type.baseFields()) {
            Object stored = row.get(baseField.name());
            if (stored != null) {
                values.put(baseField.name(), contentStorage.javaValue(type, baseField.name(), stored));
            }
        }

        return new EntityData(
                type.id(),
                id,
                (UUID) row.get(type.keys().uuid()),
                bundle,
                (String) row.get(type.keys().label()),
                langcode,
                readRevision,
                values);
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

    private void writeFields(EntityType type, EntityData entity, Long revisionId) {
        for (String field : fieldsOf(type, entity.bundle())) {
            if (entity.fields().containsKey(field)) {
                fieldTableStorage.write(
                        type, entity.id(), revisionId, entity.langcode(), field, entity.fields().get(field));
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

    private static EntityData withUuid(EntityData entity, UUID uuid) {
        return new EntityData(entity.entityType(), entity.id(), uuid, entity.bundle(),
                entity.label(), entity.langcode(), entity.revisionId(), entity.fields());
    }

    private static Long asLong(Object value) {
        return (value == null) ? null : ((Number) value).longValue();
    }
}
