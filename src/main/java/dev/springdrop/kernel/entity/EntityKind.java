package dev.springdrop.kernel.entity;

/**
 * The two kinds of entity type. Content entities live in database tables and are
 * fieldable; config entities are definitions kept in the config store and
 * exported with the rest of a site's configuration.
 */
public enum EntityKind {

    CONTENT,
    CONFIG
}
