# Schema conventions

Naming and ownership rules for SpringDrop's database schema.

## Migration layout

- `db/migration/core` holds the kernel baseline and core migrations.
- `db/migration/<module>` holds each module's migrations.

Core migrations run first; module migrations run in module dependency order once
the module system manages them. Versioned files follow Flyway's `V<n>__<name>.sql`.

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
