# Entity API

SpringDrop keeps Drupal's split between content entities and config entities, because
storage, validation, and the admin UI all follow from it.

## Entity types

An `EntityType` names the type, the class that carries it, the keys that identify it
(id, uuid, bundle, label, langcode, revision), what it supports (fieldable,
revisionable, translatable), the storage and access handlers it uses, and the routes
that link to it.

```java
EntityType.content("node", Node.class)
        .withBundleKey("type")
        .withRevisions()
        .withTranslations()
        .withLinks(Map.of("canonical", "/node/{node}"));

EntityType.config("node_type", NodeType.class);
```

A module contributes types by registering an `EntityTypeProvider` bean.
`EntityTypeManager` gathers them, resolves a type's handlers, and installs or removes
its storage when the module is installed or uninstalled.

## Storage handlers

`ContentEntityStorage` creates the base table, and the revision table for a
revisionable type, and reads and writes rows by id. `ConfigEntityStorage` keeps each
entity as one config object named `<entity type>.<id>`, so config entities export with
the rest of the site's configuration. A type names another handler by pointing at
another `EntityStorage` bean.

## Bundles

A bundled content type names the config entity type whose entities define its bundles,
and each bundle carries the fields entities of that bundle hold:

```java
bundleManager.save("node", new BundleDefinition("article", "Article", List.of("body", "tags")));
```

A bundle carries the fields instanced on it. `BundleFieldMap` answers which fields
those are, and the Field API implements it from the field instances, so loading an
entity of one bundle never carries another bundle's fields.

## CRUD

`EntityCrudService` saves, loads, and deletes content entities as `EntityData`: the
keys that identify the entity plus its field values in one language.

A save runs the constraints from every `EntityConstraintProvider` first and throws
`EntityValidationException` with the violations if any fail, so nothing is written.
It then writes the base row, the revision row, and every field table inside one
transaction, publishing `EntityEvent` at `PRESAVE` and then `INSERT` or `UPDATE`. A
listener that throws rolls the whole save back.

A load reads the base row, then the field values for the requested language and
revision, and publishes `LOAD`. Passing no revision reads the one the base row points
at. A delete publishes `DELETE`, removes every field row, and drops the base row.

A field's current values live in `<base table>__<field>`, keyed by entity, language,
and delta; a revisionable type keeps every revision's values in
`<base table>_revision__<field>` alongside them. A field holding one value reads back
as that value, one holding several as a list in delta order, and deltas are reindexed
from zero on every save.

Base fields are declared in code rather than configuration and stored as columns of
the base and revision tables. `BaseFieldDefinition.authoredContent()` is the set
authored content carries: `status`, `created`, `changed`, and `owner`. Their values
travel in the same `fields` map as configured fields; storage routes them by name.

## Fields

`FieldConfigManager` owns the two config entities behind a field:

- `FieldStorageConfig` (`field.storage.<entity type>.<field>`) is shared by every
  bundle using the field: its type, its cardinality, and its storage settings.
  Creating one builds the field's tables; deleting one drops them along with every
  instance of the field.
- `FieldInstanceConfig` (`field.instance.<entity type>.<bundle>.<field>`) is the
  field as one bundle carries it: label, description, required, and default value.

```java
fields.createStorage(FieldStorageConfig.multiple("tags", "node", "string", FieldStorageConfig.UNLIMITED));
fields.createInstance(FieldInstanceConfig.of("tags", "node", "article", "Tags").asRequired());
```

`FieldConstraintProvider` turns that configuration into constraints on save: a
required instance must be filled in, a field may not hold more values than its
cardinality allows, and the field type's own constraints apply.

### Field types

A field type is a plugin registered with `@SpringDropPlugin(type = FieldType.class)`.
It declares the properties one value holds, the constraints every field of the type
carries, the widget and formatter that handle it by default, and its default storage
and instance settings. Creating a storage on a type that is not registered is
rejected.

| Type | Stores | Bounded by |
| --- | --- | --- |
| `string` | single-line text | `max_length` storage setting, 255 by default |
| `string_long` | text of any length | nothing |
| `integer` | whole numbers | `min` and `max` instance settings |
| `decimal` | exact numbers | `min` and `max`, with `precision` and `scale` in storage |
| `float` | approximate numbers | `min` and `max` instance settings |
| `boolean` | true or false | `on_label` and `off_label` name the two states |
| `list_string`, `list_integer`, `list_float` | one of a fixed set | the storage's `allowed_values` |
| `timestamp` | seconds since the epoch | nothing |
| `datetime` | an ISO-8601 date, or date and time | the storage's `datetime_type` |
| `daterange` | a start and an end | both parse, and the end is not before the start |
| `email` | an email address | the address is well formed |
| `telephone` | a telephone number | the characters are ones numbers are written with |
| `link` | a uri and an optional title | the uri parses, internally or absolutely |
| `entity_reference` | the id of another entity | the target exists, per the storage's `target_type` |

A list field's options are stored as value and label pairs, built with
`AllowedValues.setting(...)`, so each option keeps its own type. Saving a value
outside the current set is rejected.

A field type's constraints run against each value a field holds, so a multi-valued
field is checked item by item while cardinality sees the whole list.

An internal link is written `internal:/path`. `LinkResolver` resolves it to the route
serving that path, and reports an absolute uri as external. `EntityReferenceResolver`
loads the entities a reference field points at, in the order the field holds them;
references are stored as ids and resolved when asked for, so loading an entity does
not drag its reference graph along.

## Queries

`EntityQueryExecutor.query(typeId)` builds a query a clause at a time. Conditions name
base keys and field names alike, and the executor joins whatever tables the named
properties live in:

```java
List<Object> ids = queries.query("node")
        .inLanguage("en")
        .condition(Condition.equal("status", true))
        .condition(Condition.anyOf(
                Condition.equal("category", "news"),
                Condition.equal("category", "events")))
        .sort(Sort.descending("created"))
        .range(0, 20)
        .accessTag("node_access")
        .ids();
```

`count()` runs the same conditions as a count. `inLanguage` and `inRevision` narrow
field conditions to one translation and one revision.

A query carrying an access tag is published as an `EntityQueryAlterEvent` before it
runs, so the access system and modules can add conditions to it, the way Drupal's
`hook_query_alter` narrows a tagged query.

## Access

`EntityAccessHandler` decides an operation (`view`, `update`, `delete`) on an entity
and returns an `AccessResult`, so the decision carries the cache contexts and tags it
was made from. `DefaultEntityAccessHandler` grants to a user holding
`administer <entity type>`; a type with rules of its own names its own handler.

## Validation

Constraints are plugins registered with `@SpringDropPlugin(type = Constraint.class)`.
Core ships `not_null`, `length`, `range`, `regex`, `allowed_values`, `unique_field`,
and `valid_reference`. A `ConstraintSpec` attaches one to a dot-separated property
path with its options, and `Validator` returns a `ConstraintViolation` per failure,
each bound to the path of the value that failed:

```java
List<ConstraintViolation> violations = validator.validate(article, List.of(
        ConstraintSpec.on("title", NotNullConstraint.ID),
        ConstraintSpec.on("title", LengthConstraint.ID, Map.of("max", 255))));
```

`unique_field` and `valid_reference` ask storage through the `ValueLookup` passed with
the validation. `ValueLookup.permissive()` is used when there is no storage to
consult, such as validating an object that is not attached to one.
