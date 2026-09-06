package dev.springdrop.kernel.entity;

import java.util.List;
import java.util.Map;

/**
 * The definition of an entity type: what it is called, the class that carries
 * it, which keys identify it, what it supports, where it is stored, who may act
 * on it, and the routes that link to it. Content types default to the content
 * storage handler over their own tables; config types default to the config
 * storage handler over the config store. A bundled type names the config entity
 * type whose entities define its bundles.
 */
public record EntityType(
        String id,
        Class<?> entityClass,
        EntityKind kind,
        EntityKeys keys,
        boolean fieldable,
        boolean revisionable,
        boolean translatable,
        String baseTable,
        String bundleEntityType,
        List<BaseFieldDefinition> baseFields,
        Class<? extends EntityStorage> storageHandler,
        Class<? extends EntityAccessHandler> accessHandler,
        Map<String, String> links) {

    public EntityType {
        links = Map.copyOf(links);
        baseFields = List.copyOf(baseFields);
    }

    public static EntityType content(String id, Class<?> entityClass) {
        return new EntityType(
                id,
                entityClass,
                EntityKind.CONTENT,
                EntityKeys.content(),
                true,
                false,
                false,
                id,
                null,
                List.of(),
                ContentEntityStorage.class,
                DefaultEntityAccessHandler.class,
                Map.of());
    }

    public static EntityType config(String id, Class<?> entityClass) {
        return new EntityType(
                id,
                entityClass,
                EntityKind.CONFIG,
                EntityKeys.config(),
                false,
                false,
                false,
                null,
                null,
                List.of(),
                ConfigEntityStorage.class,
                DefaultEntityAccessHandler.class,
                Map.of());
    }

    /** The table holding one row per revision, for a revisionable type. */
    public String revisionTable() {
        return baseTable + "_revision";
    }

    /**
     * Declares that entities of this type come in bundles: the column carrying
     * the bundle, and the config entity type whose entities define the bundles.
     */
    public EntityType withBundles(String bundleKey, String bundleTypeId) {
        return new EntityType(id, entityClass, kind, keys.withBundle(bundleKey), fieldable, revisionable,
                translatable, baseTable, bundleTypeId, baseFields, storageHandler, accessHandler, links);
    }

    public EntityType withRevisions() {
        return new EntityType(id, entityClass, kind, keys.withRevision("revision_id"), fieldable, true,
                translatable, baseTable, bundleEntityType, baseFields, storageHandler, accessHandler, links);
    }

    public EntityType withTranslations() {
        return new EntityType(id, entityClass, kind, keys, fieldable, revisionable, true,
                baseTable, bundleEntityType, baseFields, storageHandler, accessHandler, links);
    }

    public EntityType withoutFields() {
        return new EntityType(id, entityClass, kind, keys, false, revisionable, translatable,
                baseTable, bundleEntityType, baseFields, storageHandler, accessHandler, links);
    }

    public EntityType withAccessHandler(Class<? extends EntityAccessHandler> handler) {
        return new EntityType(id, entityClass, kind, keys, fieldable, revisionable, translatable,
                baseTable, bundleEntityType, baseFields, storageHandler, handler, links);
    }

    /** Declares the fields this type stores as columns of its own tables. */
    public EntityType withBaseFields(List<BaseFieldDefinition> fields) {
        return new EntityType(id, entityClass, kind, keys, fieldable, revisionable, translatable,
                baseTable, bundleEntityType, fields, storageHandler, accessHandler, links);
    }

    public EntityType withLinks(Map<String, String> routeLinks) {
        return new EntityType(id, entityClass, kind, keys, fieldable, revisionable, translatable,
                baseTable, bundleEntityType, baseFields, storageHandler, accessHandler, routeLinks);
    }
}
