# Schema conventions

Naming and ownership rules for SpringDrop's database schema.

## Migration layout

- `db/migration/core` holds the kernel baseline and core migrations.
- `db/migration/<module>` holds each module's migrations.

Core migrations run first, then each module's migrations in module dependency order,
so a module's tables can reference tables its dependencies created. Versioned files
follow Flyway's `V<n>__<name>.sql`.

Core tracks its migrations in `flyway_schema_history`, and each module tracks its own
in `flyway_schema_history_<module>`. Separate history tables let a module number its
migrations from `V1` without colliding with core or with another module. A module
with no migration directory is skipped and gets no history table.

## Entity tables

A content entity type owns a base table named after the type, holding one row per
entity, and a revision table named `<type>_revision` when the type is revisionable.
The base table carries the type's keys: id, uuid, label, the bundle column when the
type has bundles, `langcode` when it is translatable, and `revision_id` when it is
revisionable. Config entity types have no tables: each entity is one config object
named `<entity type>.<id>`.

## Static vs. dynamic schema

- Static tables (the config store, key/value stores, and later the entity base and
  revision tables) are created by versioned Flyway migrations.
- Dynamic per-field tables are created at runtime by `SchemaManager` because they
  depend on field configuration that does not exist at migration time.

## Dynamic table naming

`SchemaManager` builds field storage tables with predictable names so they can be
located without a registry lookup:

- `<entity>__<field>` for a field's current-value table.
- `<entity>_revision__<field>` for its revision table.

Each field table is keyed by entity id, revision id, langcode, and delta. JSON
payloads use `jsonb`; published-content listings use partial indexes.
