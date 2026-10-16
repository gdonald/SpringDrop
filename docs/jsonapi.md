# JSON:API

Every entity type is served over [JSON:API 1.0](https://jsonapi.org/format/1.0/) under
`/jsonapi`, with no per-type setup. Responses and request bodies use the media type
`application/vnd.api+json`.

## Resource types

A resource type is one bundle of a content entity type, named
`<entity type>--<bundle>`, such as `node--article`. An entity type without bundles repeats
its own name, such as `user--user`. Config entity types, such as `node_type--node_type`,
are served read-only. `GET /jsonapi` links to the collection of every resource type.

| Method | Path | Answers |
| --- | --- | --- |
| `GET` | `/jsonapi` | the links to every collection |
| `GET` | `/jsonapi/{type}/{bundle}` | 200 with one page of the collection |
| `GET` | `/jsonapi/{type}/{bundle}/{id}` | 200 with the resource |
| `POST` | `/jsonapi/{type}/{bundle}` | 201 with the resource and a `Location` header |
| `PATCH` | `/jsonapi/{type}/{bundle}/{id}` | 200 with the changed resource |
| `DELETE` | `/jsonapi/{type}/{bundle}/{id}` | 204 |

A content entity's `id` is its uuid, and a config entity's is its machine name. A client
signs in with a session cookie or a bearer token, as
[API authentication](api-authentication.md) describes.

## Resource objects

A content entity's `attributes` are `drupal_internal__id` (its numeric id), `label`,
`langcode`, its base fields, and its configurable fields. Its `relationships` are `owner`,
for a type with owners, and each entity reference field, each holding resource identifiers
of the entities it points at. A config entity's attributes are `drupal_internal__id` and its
stored values.

A resource leaves out the fields the person may not view, by the field access rules in
[the entity API](entity-api.md#field-access). An account's `password_hash` is never sent.

## Reading collections

| Parameter | Example | Does |
| --- | --- | --- |
| `filter` | `filter[label]=Spring schedule` | keeps the resources matching every filter |
| `sort` | `sort=-priority,label` | orders by each path in turn, `-` for descending |
| `page[offset]`, `page[limit]` | `page[offset]=20&page[limit]=10` | one page, at most 50 resources |
| `fields[<type>]` | `fields[node--article]=label,tags` | sends only the named fields of that type |
| `include` | `include=tags,related.tags` | adds the entities those relationships point at under `included` |

A filter takes one of three forms:

- `filter[path]=value` compares for equality.
- `filter[path][value]=value&filter[path][operator]=CONTAINS` names an operator.
- `filter[name][condition][path|operator|value]` names a condition, which can be a member
  of a named group `filter[name][group][conjunction]=OR` through
  `filter[name][condition][memberOf]`. Groups nest the same way.

The operators are `=`, `<>`, `>`, `<`, `IN`, and `CONTAINS`. `IN` takes a list,
`filter[path][value][]=a&filter[path][value][]=b`. A path is an attribute, or `<relationship>.id`
with `=` or `IN` and the uuids of the entities it points at. A value is read as the kind
the attribute holds: a whole number, a number, `true`/`false`/`1`/`0`, a uuid, or a date and
time with an offset.

A collection is ordered by its sorts and then by id. `meta.count` is the number of
resources the filter matches, and `links` carries `self`, and `prev` and `next` when there
are pages before or after. A resource the person may not view is left out of the page and
of `included`, and named under `meta.omitted`. Config collections are filtered, sorted, and
paged the same way, with numbers compared as numbers and anything else as text.

## Writing

Writes are refused with 405 until `/admin/config/services/jsonapi` is set to accept all
create, read, update, and delete operations. The page needs `administer site configuration`.
Config entities are never written.

A body is a document whose `data` is a resource object of the endpoint's type:

```json
{"data": {"type": "node--article",
  "attributes": {"label": "Spring schedule", "summary": "Opening hours"},
  "relationships": {"tags": {"data": [{"type": "taxonomy_term--tags", "id": "5d1f..."}]}}}}
```

`label` and `langcode` set the entity's label and language, and the language of a saved
entity cannot change. Attributes write base and configurable fields as REST does, and
relationships write the reference fields by the uuids of their targets. `null` or an empty
relationship clears a configurable field. A `PATCH` changes the members it names and leaves
the rest. A new entity is owned by the account that sent it.

A write needs the entity access it stands for (create, asked about the bundle, update, or
delete) and edit access to every field it names. The entity is validated before it is saved.

## Errors

A refusal is a document with an `errors` list, each error carrying `status`, `title`,
`detail`, and, for a problem with a member of the body, `source.pointer`.

| Status | When |
| --- | --- |
| 400 | a query parameter or the body cannot be read, or a filter, sort, or include names something that is not there |
| 401 | someone not signed in is refused what a 403 refuses a signed-in account, or a bearer token is not valid |
| 403 | the entity access is missing, a filter or sort names a field the person may not view, a write names a field they may not edit, or a `POST` gives an id |
| 404 | no such resource type or resource |
| 405 | writes are not accepted, or the resource type is a config entity type |
| 409 | the body's type or id does not match the endpoint |
| 415 | the body is not sent as `application/vnd.api+json`, or carries media type parameters other than a charset |
| 422 | a field cannot be written or cleared, a relationship points at nothing, or the entity breaks a constraint |
