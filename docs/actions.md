# Actions

An action does one thing to one entity: publish a node, block an account. Actions are
applied to a selection of a listing's results through its bulk form, and each application
is logged.

## Action plugins

An action is a plugin of type `Action`:

| Method | What it gives |
| --- | --- |
| `label()` | the name the action is offered under |
| `entityType()` | the type of entity it works on |
| `destructive()` | whether applying it to a selection asks for confirmation first (default no) |
| `configurable()` | whether it needs settings (default no) |
| `configurationForm(configuration)` | the controls for its settings |
| `validate(configuration)` | what is wrong with its settings, by setting name |
| `access(entity, who)` | whether the person may apply it to the entity |
| `execute(entity, configuration, who)` | the change |

Core ships these:

| Id | Label | Entity | Access |
| --- | --- | --- | --- |
| `node_publish` | Publish content | node | update access, refused for a content type under a workflow |
| `node_unpublish` | Unpublish content | node | update access, refused for a content type under a workflow |
| `node_promote` | Promote content to front page | node | update access |
| `node_unpromote` | Remove content from front page | node | update access |
| `node_make_sticky` | Make content sticky | node | update access |
| `node_make_unsticky` | Make content unsticky | node | update access |
| `node_save` | Save content | node | update access |
| `node_delete` | Delete content (destructive) | node | delete access |
| `node_assign_owner` | Change the author of content (configurable: `owner`, a username) | node | update access |
| `user_block` | Block account | user | `administer users`, never the anonymous account |
| `user_unblock` | Unblock account | user | `administer users`, never the anonymous account |

Every node action saves the node as a new revision by the person applying it.

## Simple and configurable actions

`ActionManager.actions()` gives every action the site offers, by label. A simple action
is offered under its plugin id. A configurable action is offered once an `ActionConfig`
(id, label, plugin, configuration) is stored for it as the config object `action.<id>`.
`save(action)` refuses an unknown plugin or settings the plugin's `validate` rejects, and
gives the errors.

`/admin/config/system/actions` lists the actions. Choosing a configurable plugin there
opens a form for its label and settings, and the id is made from the label. A stored action
has Edit and Delete buttons. The page needs `administer site configuration`.

## Applying an action

`ActionManager.execute(actionId, entity, who)` checks the action works on the entity's
type and that the person may apply it, throwing `AccessDeniedException` and changing
nothing when they may not. It then applies the action and logs a notice on the `action`
channel of the activity log: `{action} was applied to {entity_type} {label} ({id}).`

## Bulk operations

A view gets a bulk form by having a `bulk_form` field. The field draws a box on each
result, sent as `items=<type>:<id>`. Its `actions` setting names the actions offered, by
id and separated by commas. Left empty, every action for the view's base entity type is
offered. The default `content` view has one.

A page display whose results include a bulk form field is drawn with the bulk form above
the listing: an action select, an **Apply to selected items** button, the form token, and
the listing's address with its filters as the destination. The listing's boxes join the
form through their `form` attribute. The form is drawn for each request, outside the
cached listing, so the cache never holds a form token.

`/js/bulk-selection.js` keeps the apply button disabled until a box is ticked, and the
browser will not submit without an action chosen. The server refuses both cases as
well, answering 400 with **Choose an action.** or **Select one or more items.**, and
refuses an item that is not of the view's base type with **Choose items from the
listing.**

The form posts to `/views/bulk/{view}/{display}`, which needs access to the listing. A
destructive action first shows the items and asks for confirmation. The action then runs
as a batch, an `action` operation per item, as the person watching the batch. An item
deleted since it was ticked, or one the person may not apply the action to, is reported
in the batch's errors and left as it was. The batch finishes at the destination when it
is a path on the site, and at the listing otherwise.
