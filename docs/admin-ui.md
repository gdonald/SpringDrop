# Admin UI

## Toolbar and sections

The administration toolbar and the section overview pages are described in
[the menu system](menu-system.md#the-administration-menu-and-toolbar).

## Listings

Every admin overview, a page listing things to act on row by row, follows one pattern, drawn
by the fragments in `templates/fragments/actions.html`:

| Part | Fragment | Draws |
| --- | --- | --- |
| Local tasks | `localTasks(${localTasks})` | the tab bar of the pages that share a listing, under the title |
| Local action | `localAction(label, href)` | a primary button above the listing, such as "Add image style" |
| Table | | a Bootstrap table with the `admin-listing` class |
| Edit | `edit(href)` | the row's Edit button |
| Delete | `delete(href)` | the row's Delete button |
| Other row actions | `operation(label, href)`, `destructive(label, href)`, `choose(label, href)` | a secondary button, a red button for removing something not called Delete, and a primary button for a row's main choice |
| Empty listing | `empty(message)` | what the listing says when it has nothing to list |

Every button is a Bootstrap button, never outline-style, and Edit is always a button.
`AdminListingTemplateTest` checks each admin template with a table: an overview must draw its
tables with `admin-listing`, include the local tasks, and draw Edit and its other row actions
through the fragments. A template with a table that is not an overview, such as a form or a
report, is named in the test.

Listings drawn by a view, such as `/admin/content`, draw their filters, bulk actions, and pager
through the view, and get their tabs and local actions from the page chrome.

## Local tasks

`AdminLocalTasks` names the groups of pages that share a tab bar, and `AdminLocalTasksAdvice`
gives each view the tabs of its page:

| Group | Tabs |
| --- | --- |
| Content | Content (`/admin/content`), Comments, Media, Blocks |
| People | People, Permissions |

The page's own tab is marked active. `/admin/content` gets an "Add content" local action.

## Status messages

`Messenger` keeps status messages, such as "Saved.", in the person's session until a page
shows them, once. An action adds a message and redirects, and the page it lands on shows it:
a page drawn in a theme layout through the messages partial, with any messages its own
chrome carries, and a standalone page through `~{fragments/messages :: messages}`. The
messages are taken from the session only when a page draws them, so a redirect does not use
them up, and a page showing messages is not cached.

| Action | Message |
| --- | --- |
| Saving content | "<Content type> <title> has been created." or "... has been updated." |
| Deleting content | "<Content type> <title> has been deleted." |
| Saving a settings form | "The configuration options have been saved." |
| Clearing the caches | "Caches cleared." |
| Exporting configuration, or importing a single item | what was exported or imported |

## Contextual links

A placed block carries a "Configure block" button for someone holding `administer blocks`,
and a piece of content carries "Edit" and "Delete" buttons for someone who may change or
remove it. The links are a placeholder, `contextual_links`, drawn in the `contextual` slot
of the block and node templates and built for each request, so a kept page shows each person
their own. Someone who may follow none sees nothing.

## Help

`CoreHelp` gives a page's own help, such as the block layout's, and otherwise the help of the
section it is in: Extend, Content, Structure, Configuration, People, or Reports. A themed page
shows it in the help block, and a standalone admin page through
`~{fragments/help :: help}`, under the status messages.
