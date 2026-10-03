# Content

Nodes are the site's content: articles, pages, and whatever other kinds a site adds.
`NodeEntityType` declares `node` as a content entity that comes in bundles, keeps a
revision on every save, and is translatable. A node's label is its title.
`NodeService.install` creates the node tables when the application starts, leaving tables
that exist as they are.

## Content types

A content type is the bundle a node belongs to, stored as the config entity
`node_type.<id>` and read and written through `NodeTypeManager`. `NodeType` holds:

| Property | Meaning |
| --- | --- |
| `label` | the name the admin and the add-content page list the type as |
| `description` | shown on the add-content page next to the name |
| `help` | explanation or submission guidelines, shown above the node form |
| `titleLabel` | what the node form calls the title field, `Title` by default |
| `published`, `promoted`, `sticky` | how a new node of the type starts out |
| `newRevision` | whether saving a node of the type creates a new revision by default |

`/admin/structure/types` lists the types, with buttons to edit each one and to manage its
fields, form display, and display through the Field UI. Adding a type generates its
machine name from the label, suffixed when another type already has it, and opens its
fields. A type that nodes are still written in cannot be deleted. The pages require
`administer content types`.

## Base fields

Every node carries these in its base and revision tables, alongside the fields its type
adds:

| Field | Holds |
| --- | --- |
| `status` | whether the node is published |
| `owner` | the id of the account that wrote it, `0` for someone not signed in |
| `created`, `changed` | when it was created and last saved |
| `promote`, `sticky` | whether it is listed on the front page, and listed first |
| `revision_log` | what the person saving a revision wrote about it |
| `revision_user` | the account that saved the revision |
| `revision_created` | when the revision was saved |
| `revision_default` | whether the revision is the one the site shows |
| `layout_builder__layout` | the node's own layout, when its type allows one |
| `moderation_state` | the workflow state of the revision, when a workflow moderates the type |

`NodeService.create` starts a node from its type's publishing defaults, owned by the
given account. `NodeService.save(node, account, newRevision, log)` stamps `created` on a
new node and `changed` on every save, records the saving account as `revision_user` and
the log message as `revision_log`, and stamps `revision_created` on each new revision.
Saving without a new revision writes over the current one.

## Writing and reading

| Path | Does |
| --- | --- |
| `/node/add` | lists the content types the person may create content of |
| `/node/add/{type}` | the form for a new node of that type |
| `/node/{id}` | the node on its own page, in the `full` view mode, with View, Edit, and Layout tabs |
| `/node/{id}/edit` | the form for an existing node |

The form holds the title, labeled the way the type says, and the fields the type's form
display shows. A field the form shows and the submission leaves empty is emptied. A
submission that breaks a form rule or a field constraint comes back with the error on
its field and nothing saved.

`NodeService.build(node, viewMode, page)` draws a node through the `node` template, with
suggestions by id, bundle, and view mode. Its fields are laid out by the type's layout for
that view mode when Layout Builder draws it, and by the type's display otherwise. Outside its own page the template links the title to the node. What
a node renders carries the `node:<id>` cache tag.

## Revisions

The node form's Revision information holds a log message, and on an existing node a
"Create new revision" box that starts the way the content type's `newRevision` says.

| Path | Does |
| --- | --- |
| `/node/{id}/revisions` | lists the revisions newest first, with date, author, and log message |
| `/node/{id}/revisions/{revision}/view` | shows the node as that revision held it |
| `/node/{id}/revisions/diff?left=&right=` | compares two revisions word by word, the older on the left |
| `/node/{id}/revisions/{revision}/revert` | saves that revision's content as a new revision, after a confirm |
| `/node/{id}/revisions/{revision}/delete` | deletes that revision, after a confirm |

The comparison shows the title and each field of the content type, with removed words
struck and added words underlined, using `TextDiff`. The revision the site shows can be
neither reverted to nor deleted, and a revert's log message names the revision it copied.

`NodeRevisionAccess` decides who may. Reading the history takes `view <type> revisions`
or `view all revisions` and being allowed to read the node. Reverting takes
`revert <type> revisions` or `revert all revisions` and being allowed to edit the node.
Deleting takes `delete <type> revisions` or `delete all revisions` and being allowed to
delete the node. `bypass node access` and the first account cover the revision
permission. The node page shows a Revisions tab to someone who may read the history.

## Front page

`/node` lists front page content: published nodes promoted to the front page, sticky
ones first and then the newest, ten to a page as teasers, with a pager when there is more
than one page. A page number out of range reads as the nearest page, and one that is not
a number reads as the first. Nodes the person may not read are left out, and an empty
listing says that no front page content has been created yet.

The site's home, `/`, shows the page at the path `system.site` names as `frontPage`.
That is `/node` unless the site names another path, and a front page left blank or set to
`/` reads as `/node`.

## Access

`NodeAccessHandler` decides who may do what with a node:

| Operation | Allowed by |
| --- | --- |
| view a published node | `access content` |
| view an unpublished node | `view any unpublished content`, or being its owner and holding `view own unpublished content` |
| edit | `edit any <type> content`, or owning it and holding `edit own <type> content` |
| delete | `delete any <type> content`, or owning it and holding `delete own <type> content` |
| create | `create <type> content`, asked about the type's id rather than a node |

`bypass node access` allows every operation. An answer that depends on who owns the node
varies by the `user` cache context as well as `user.permissions`. The five per-type
permissions come from `NodePermissions`, which builds them from the content types the
site has.
