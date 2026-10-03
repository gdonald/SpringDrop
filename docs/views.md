# Views

A view is a stored listing of entities: which entities of one type to find, in what
order, and how to draw them as a page, a block, or an RSS feed. Views are config, edited
at `/admin/structure/views`, and the site's own listings (the front page, the content and
files overviews, the people list, and a taxonomy term's content) are views.

## The view config

`ViewConfig` is stored as `views.view.<id>` through `ViewManager`: an id, a label, a
description, the base entity type it lists, and its displays. Every view has a display
`default`, whose options the other displays inherit.

`ViewOptions` holds the parts of a query and its drawing:

| Part | Holds |
| --- | --- |
| `fields` | the columns or values drawn for each result |
| `filters` | conditions every result meets |
| `sorts` | the order of the results |
| `arguments` | contextual filters, taking their values from the path |
| `relationships` | other entities each result reaches, which handlers can read |
| `pager` | how many results are shown and how they are paged |
| `style` | how the results are laid out |
| `row` | how each result is drawn |
| `access` | who may see the view |

The first five are lists of `HandlerConfig`: an id unique in its list, the plugin that
handles it, the relationship it reads through (`none` for the base entity), the property
it reads, and its settings. The last four are a `PluginConfig`: a plugin id and settings.

A display other than `default` leaves a part `null` to use the default display's, and
gives its own value to override it. `ViewConfig.options(display)` gives the options a
display runs with, the default's filled in where the display has none.

## Displays

`ViewDisplay` has an id, its plugin (`default`, `page`, `block`, or `feed`), a title, its
overrides, and its settings:

| Setting | Display | Meaning |
| --- | --- | --- |
| `path` | page, feed | the path it answers at, such as `/news` or `/news/%` |
| `menu_title` | page | the title of a link to it in a menu, none when blank |
| `menu` | page | the menu the link goes in, `main` when blank |
| `exposed_block` | page | draw the exposed form as a block of its own instead of above the results |
| `entity_access` | default | check the view access of each result, on unless set to false |
| `empty_text` | any | the text shown when there are no results |

### Pages and feeds

`ViewPageFilter` runs after the alias filter. A `GET` or `HEAD` request whose path matches
a page or feed display is handled as a request for `/views/page/<view>/<display>`, with
the values its `%` parts took kept as the contextual filter values. A path matches a
display's path when it has the same number of parts and every part other than `%` is the
same, so `/admin/content` does not answer for `/admin/content/media`. When several
displays match, the one with the most fixed parts answers.

A page display is drawn in the page chrome, titled with the display's title, or the
view's label when the display has none. A path under `/admin` is drawn in the admin theme.
A person the view's access plugin turns away is answered with 403.

A feed display answers with RSS 2.0: the channel titled with the display's title, and an
item per result with its label, a link to its page through its alias, and its creation
date as `pubDate` when it has one.

`ViewMenuLinks` adds a menu link for each page display with a `menu_title`.

### Blocks

The `views_block` deriver gives a block `views_block:<view>-<display>` for every block
display, labelled `<view label>: <display title>`. It draws nothing for a person the
view's access plugin turns away, or when the display has no results.

The `views_exposed_filter_block` deriver gives a block
`views_exposed_filter_block:<view>-<display>` for every page display with
`exposed_block` set. It draws the display's exposed form, sending the reader to the
display's path.

### Drawing a display elsewhere

`ViewEmbed.render(view, display, arguments, input, path, exposedForm)` draws a display
inside another page for the person making the request, or nothing for a view the site no
longer has or one the person may not see. The front page, the people list, and a taxonomy
term's page draw their listings this way.

## Running a view

`ViewExecutor.execute(view, display, arguments, input, page)` builds an entity query
against the base entity type, with the base type's access tag, from the display's
options:

- Each filter adds the condition its plugin makes from its `value` setting, or for an
  exposed filter, from the reader's value under its `identifier`. A filter that makes no
  condition, such as an exposed one left blank, adds none.
- Each contextual filter takes the path value at its position. Without one, its
  `default_action` decides: `ignore` adds nothing, `empty` finds nothing, and `fixed`
  uses its `default_value`. A value its plugin cannot read finds nothing.
- A filter or contextual filter on a relationship's entity finds the entities of the
  target type matching it, then keeps the results whose relationship property names one
  of them.
- The sorts apply in order. A table sorted by a column (`order`, with `sort` set to `asc`
  or `desc`) is sorted by that first. Then come the orders the base entity's filters give
  through `FilterHandler.sort`, such as a search's ranking. Then a sort marked `exposed`
  lets the reader choose it through `sort_by` and `sort_order` (`asc` or `desc`), the
  first exposed sort by default, and the other sorts follow. Sorts on a relationship's
  entity are passed over.
- The pager sets the range, and the page asked for is held between the first and the last
  page.

Each result is loaded and, unless the default display sets `entity_access` to false, kept
only when the reader may view it. A `ResultRow` holds the result, the entities its
relationships reach, and the reader's values, so a field handler can read them. A result's relationships are loaded as well, each kept
only when the reader may view it. A handler whose plugin the site lacks is passed over.

`ViewExecutor.mayAccess(view, display, authentication)` asks the view's access plugin,
and allows everyone when the plugin is missing.

## Plugins

Every plugin is registered with `@SpringDropPlugin(type = <interface>.class)`, and gives a
label, a settings form, and the settings a submission of the form gives.

### Fields

| Id | Label | Draws |
| --- | --- | --- |
| `entity_label` | Label | the label, linked to the entity's page unless `link` is off |
| `base_value` | Value | a base property as text, a boolean as `true_label` or `false_label` |
| `entity_field` | Field | a configured field through the formatter `formatter` names, or the field type's default |
| `entity_operations` | Operations | an Edit button where the reader may change the entity |
| `entity_links` | Links | the `links` setting's buttons, each a `label`, a `path` with `{id}` for the entity's id, and an optional Bootstrap `style` |
| `search_excerpt` | Search excerpt | the excerpt of the result's search document for the reader's keywords, described in [Search](search.md) |

Every field takes a `label`, its column heading.

### Filters

| Id | Label | Condition |
| --- | --- | --- |
| `value` | Value | the property compared with the value by `operator`: `=`, `!=`, `<`, `>`, `contains`, or `in` with values separated by commas |
| `user_role` | Role | accounts holding the role |
| `search_keywords` | Search keywords | results whose search documents hold the keywords, best match first, described in [Search](search.md) |

The `value` filter reads the value as its `type` setting says: `text`, `number`, or
`boolean`. A value that is not of the type finds nothing. A filter marked `exposed` is
drawn on the exposed form, a text field, a yes and no choice for a boolean, or a choice
of the site's roles.

### Sorts, contextual filters, and relationships

| Kind | Id | Label | Does |
| --- | --- | --- | --- |
| sort | `standard` | Standard | orders by the property, `order` `asc` or `desc` |
| contextual filter | `value` | Value | finds results whose property equals the value |
| contextual filter | `taxonomy_term_any` | Has term | finds content tagged with the term id through any node reference field pointing at terms |
| relationship | `reference` | Reference | reaches the entity a reference field or `owner` names, or the type `target_type` names |

### Pagers

| Id | Label | Shows |
| --- | --- | --- |
| `full` | Paged output, full pager | `items_per_page` at a time, with links to every page |
| `mini` | Paged output, mini pager | `items_per_page` at a time, with Previous and Next links |
| `some` | Display a specified number of items | `items_per_page` results and no pager |
| `none` | Display all items | every result and no pager |

Every pager takes an `offset`, the number of results skipped first. An `items_per_page`
of 0 shows every result.

### Styles and rows

| Kind | Id | Label | Draws |
| --- | --- | --- | --- |
| style | `default` | Unformatted list | each result in a `div` |
| style | `html_list` | HTML list | a `ul`, or an `ol` when `type` is `ol` |
| style | `grid` | Grid | a Bootstrap grid of `columns` to a row |
| style | `table` | Table | a Bootstrap table, a column per field, with sorting headings when `sortable` is on |
| row | `fields` | Fields | each field, with its label when `labels` is on |
| row | `entity` | Rendered entity | the entity in the view mode `view_mode` names, `teaser` by default |

### Access

| Id | Label | Allows |
| --- | --- | --- |
| `none` | Unrestricted | everyone |
| `permission` | Permission | those holding `permission` |

## The exposed form

A display with exposed filters or sorts draws a Bootstrap form above its results, or in
its own block. It sends a `GET` to the display's path, and with htmx, replaces the
display's element, `view-<view>-<display>`, with the same element of the answer and
pushes the new address. The pager's and table headings' links keep the reader's values.

## Caching

`ViewCache` keeps each drawing, keyed by the view, the display, the contextual filter
values, the reader's values, the path, and the reader's permissions. A drawing carries
the tag of the view's config and the list tag of its base entity type and each type its
relationships reach. When a tag is invalidated, as saving or deleting an entity of a
listed type or changing the view does, the drawings carrying it are dropped. See
[the render cache](render-pipeline.md#cache-tags).

## Default views

`DefaultViews` adds these views when the site starts, leaving a view already saved under
the id as it is:

| Id | Lists | Displays | Access |
| --- | --- | --- | --- |
| `content` | all content in a sortable table, latest change first, with exposed title, type, and status filters | page `/admin/content` | `access content overview` |
| `files` | managed files in a sortable table, newest first, with exposed name and status filters | page `/admin/content/files` | `access files overview` |
| `frontpage` | published content promoted to the front page as teasers, sticky first, then newest | the front page, and feed `/rss.xml` | `access content` |
| `taxonomy_term` | published content tagged with the term as teasers | a term's page | `access content` |
| `people` | accounts by name with Edit and Close buttons, with exposed name, status, and role filters | `/admin/people` | `administer users` |

`access content overview` and `access files overview` are declared in
`views.permissions.yml`.

## The Views UI

`/admin/structure/views` lists the views with their displays, and needs `administer views`.

| Action | Request |
| --- | --- |
| add a view | `GET`, `POST .../add` with `label`, `base`, and an optional page `path` |
| edit a view's display | `GET .../view/<id>?display=<display>` |
| save a display's settings | `POST .../view/<id>/display/<display>/settings` |
| add a display | `POST .../view/<id>/displays/add` with `plugin` `page`, `block`, or `feed` |
| delete a display | `POST .../view/<id>/display/<display>/delete` |
| override a part, or use the default's again | `POST .../view/<id>/display/<display>/override/<part>` |
| add a handler | `GET`, `POST .../view/<id>/display/<display>/<section>/add` with `plugin` and `target` |
| configure a handler | `GET`, `POST .../view/<id>/display/<display>/<section>/<handler>` |
| remove a handler | `POST .../view/<id>/display/<display>/<section>/<handler>/remove` |
| choose and configure a plugin | `GET`, `POST .../view/<id>/display/<display>/plugin/<kind>` |
| delete a view | `GET`, `POST .../view/<id>/delete` |

A new view gets a machine name from its label and a page display when a path is given.
A display path is checked in the browser and on the server against the pattern and length,
and on the server against the paths the site serves. A handler's target is a property of
the base entity, or of a relationship's entity, written `<relationship>|<property>`. Its
id is the property's, numbered when the display already has one, and never `add`.
Editing a part a display inherits edits the default display's, until the display
overrides it.
