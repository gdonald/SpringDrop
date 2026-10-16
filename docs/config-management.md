# Configuration management

The site's configuration, config entities and simple config alike, lives in the config
store, one object per name. It can be exported to a sync directory of YAML files and
imported from one, so configuration made on one site can be moved to another and kept in
version control.

## The sync directory

`springdrop.config.sync-directory` names the directory, `config/sync` by default, read from
the directory the application runs in. Each object is a file named `<name>.yml`:

```yaml
uuid: "0d5c6f0e-3f0b-4a44-9a52-2f8a1ad1d7a4"
dependencies:
  config:
  - "field.storage.node.summary"
  - "node_type.article"
  module: []
data:
  label: "Summary"
  required: false
```

Every object keeps a uuid in the config store, made when it is first saved, and the export
carries it. The dependencies come from the object's name:

| Object | Depends on |
| --- | --- |
| `field.instance.<type>.<bundle>.<field>` | `field.storage.<type>.<field>` and the bundle's config object |
| `core.form_display.<type>.<bundle>.<mode>`, `core.view_display.<type>.<bundle>.<mode>` | the bundle's config object |
| `layout_builder.display.<type>.<bundle>.<mode>` | `core.view_display.<type>.<bundle>.<mode>` |
| an object whose name starts with a module's machine name | that module |

## Exporting

An export writes a file for every object in the config store and removes the files of
objects that are no longer there. Files in the directory that do not end in `.yml` are
left alone.

## Importing

An import compares the directory with the site:

| Change | When |
| --- | --- |
| Create | a file's object is not on the site |
| Update | a file's value or uuid differs from the object on the site |
| Rename | a file under a new name carries the uuid of an object on the site whose own name has no file |
| Delete | an object on the site has no file |

Creations, updates, and renames come first, each after the objects it depends on, and
deletions last, each before the objects it depends on. A field storage's tables are created
when an import adds the storage and dropped when an import removes it, through
`FieldStorageSyncHandler`. A module adds its own `ConfigSyncHandler` for config whose save or
delete does more than write the config store.

## Validation

An import is checked before anything is applied, and refused whole, with the site's config
left as it was, when:

- a file depends on a config object the import does not hold,
- a file depends on a module that is not enabled,
- two files carry the same uuid, or
- objects depend on each other, directly or through others, or
- a file's value does not match its schema.

A file's dependencies are those it declares together with those its name implies, so a file
edited by hand cannot drop them.

## Schemas

An object's schema is the type its value must read as. A config entity's schema is its
entity type's class, such as `NodeType` for `node_type.*`. Other objects' schemas come from
`ConfigSchemaProvider` beans: `CoreConfigSchemas` names core's, by exact name, such as
`system.site`, or by prefix, such as `user.role`. An exact name is matched before a prefix.

A value is refused when a key holds the wrong kind of value, when a key the schema does not
know is present, or when a key holding a number or a true or false value is null. The error
names the object and the key:

```
user.role.editor: weight holds heavy, which is not a whole number.
user.role.editor: color is not a key of its schema.
```

An object with no schema is not checked.

## Running an import

An import runs through the Batch API. One operation reads each changed object's file, so an
unreadable file is reported under its own name, and a last operation applies every change in
one transaction. A file that cannot be read fails that last operation too, and the site's
config stays as it was.

## Single items

`/admin/config/development/configuration/single/export` shows one object as an export
writes it, chosen from every object the site has.

`/admin/config/development/configuration/single/import` takes a config name, dot-separated
parts of lowercase letters, digits, underscores, and hyphens, and the object's value as YAML,
the `data` of an exported file. The value creates the object, with a new uuid, or replaces
the value of the object of that name, which keeps its uuid. A new field storage gets its
tables. The import is refused, with every reason, when the YAML is blank or cannot be read,
the value does not match its schema, or the object depends on config the site does not have
or on a module that is not enabled.

## The synchronize page

`/admin/config/development/configuration` lists what an import would change. Each change
other than a deletion has a button to view the object on the site and in the directory side
by side. "Import all" starts the import batch, and "Export to the sync directory" writes the
site's config there. An import that would be refused, or a file that cannot be read, is
listed with the reason in place of the import button, and a request to import then is sent
back to the page. Buttons lead to the single item pages. The pages need
`synchronize configuration`.
