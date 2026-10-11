# REST

Content entities are served over REST, one resource per entity type. A resource answers
the HTTP methods it enables, in the formats it enables.

## Resources

A resource is the config object `rest.resource.<entity type>` (`RestResourceConfig`): the
entity type, its methods (`GET`, `POST`, `PATCH`, `DELETE`), and its formats.
`/admin/config/services/rest` has a box for each method and format of each content entity
type. A type with no method ticked is not served, and one with methods but no format is
served as JSON. The page needs `administer rest resources`.

Each enabled method adds a permission, `restful <method> entity:<type>`, such as
`restful get entity:node`.

## Requests

| Method | Path | Answers |
| --- | --- | --- |
| `GET` | `/rest/{type}/{id}` | 200 with the entity |
| `POST` | `/rest/{type}` | 201 with the entity and a `Location` header |
| `PATCH` | `/rest/{type}/{id}` | 200 with the changed entity |
| `DELETE` | `/rest/{type}/{id}` | 204 |

A request needs the method's permission, and the entity access the method stands for:
view to read, create (asked about the bundle) to add, update to change, and delete to
remove. The first account needs neither. A response is in the format `?_format=` names,
JSON by default, and a request body is read in the enabled format whose media type its
`Content-Type` names.

An entity is sent as its `type`, `id`, `uuid`, `bundle`, `label`, `langcode`, and
`fields`. A body may leave any of them out. `POST` needs `bundle` for a type with bundles.
`PATCH` changes the label and the fields the body names and leaves the rest. A body may
write the bundle's configurable fields, passed as they are, and base fields holding text,
whole numbers, or `true` and `false`. The owner, dates, and revision fields are kept by the
site: a new entity is owned by the account that sent it. A node is saved through the node
service, so it gets its dates and a revision like one saved from its form.

A refusal is a JSON object with a `message`:

| Status | When |
| --- | --- |
| 400 | the body is not the format |
| 403 | the method's permission or the entity access is missing |
| 404 | no resource answers the method for the type, or no such entity |
| 406 | `_format` names a format the resource does not serve |
| 415 | the `Content-Type` names a format the resource does not read |
| 422 | a field cannot be written or its value is the wrong kind, the bundle is missing, or the entity breaks a constraint, with `errors` listing each `field` and `message` |

## Formats

A format is a `RestSerializer` plugin whose id is the format's name. Core has `json` and
`xml`. In XML the entity's keys are attributes of `<entity>`, the label is `<label>`, and
each field is a `<field name="...">` with a `<value>` per value, a map value holding an
`<entry key="...">` per key. XML values read back as text, and a body declaring a document
type is refused.
