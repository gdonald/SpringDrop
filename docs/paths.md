# Path aliases

A path alias is another path a page answers at, such as `/about` for `/node/7`. Aliases
are stored per language in the `path_alias` table: the alias, the source path it stands
for, and the langcode. An alias is unique within its language, and a source has at most
one alias per language.

## Requests and links

`PathAliasFilter` runs before the security chain. A request whose path is an alias in the
default language is handled as a request for its source: everything after the filter,
including route access checks, sees the source's path, so an alias is answered with the
access of the page it stands for. The source's own path keeps working.

`PathAliasManager.outbound(path)` is the path a link to a page uses: its alias in the
default language, or the path itself. The site uses it for:

- a node's and a media item's link in their templates, such as a teaser's title,
- the `entity_reference_label` formatter's links,
- the `image` and `responsive_image` formatters' links to content,
- menu links,
- the links in a taxonomy term's feed.

Both lookups are kept in memory once made, and any change to an alias clears what the
instance kept. Another instance of the site keeps its own until it changes an alias
itself.

## Rules for an alias

`PathAliasManager.refusal(source, alias, langcode)` gives the reason an alias is refused,
and `save` throws `IllegalArgumentException` with it:

- It is written as `/about` or `/news/2026`: segments of letters, digits, and `_ . ~ -`,
  each after a slash, with no slash at the end, in at most 255 characters.
- It is not a path the site already serves: a controller mapping, a registered route such
  as `/node/**`, or a static file under `/js/`, `/css/`, `/files/`, or `/webjars/`, or
  `/favicon.ico`. An alias cannot stand in front of a page such as the sign-in form.
- It is not taken by another source in the language.

Saving a blank alias takes the source's alias away. `deleteAll(source)` takes away a
source's aliases in every language, and deleting a node or a media item does that for its
page.

When a source's alias changes, the old alias is sent on to the new one with a permanent
redirect, and when the alias is taken away, to the source. See [Redirects](#redirects).

## The URL alias field

The node form has a URL alias section with the field `path`. It is checked against the
pattern and length in the browser and on the server, and against served paths and other
nodes' aliases on the server. A refused alias comes back on the form as it was typed.
Saving the node gives its page the alias, replaces the alias it had, or takes it away when
the field is blank.

## Alias patterns

An `AliasPattern` is a config object stored as `path.pattern.<entity type>.<bundle>`: a
pattern of text and tokens, such as `/blog/[node:created:year]/[node:title]`, and whether
an alias made from it is made again each time the page is saved, so it follows a changed
title. `AliasPatternManager.generate(entity, source)` makes an alias from the entity's
bundle's pattern:

- Each token is replaced through the token system with the entity's value, read from its
  title (`[node:title]`), id (`[node:id]`), bundle (`[node:type]`), and fields, such as
  `[node:created:year]`. A token with no value leaves nothing.
- Each value is turned to ASCII, made lower case, and every run of other characters
  becomes one dash, so `Crème Brûlée, the Recipe!` becomes `creme-brulee-the-recipe`.
- Characters an alias cannot hold in the pattern's own text become dashes, empty segments
  are dropped, and the alias is cut to 255 characters.
- When the alias is refused, because another page has it or the site serves that path, a
  number is added before it is tried again: `-1`, `-2`, and on, cutting the alias to make
  room.

A pattern that makes nothing gives no alias. An alias records whether it was made from a
pattern.

### Automatic aliases on the node form

For a content type with a pattern, the node form's URL alias section has a Generate
automatic URL alias box, which disables the `path` field while ticked. It starts ticked
for a new node, and for a node with no alias or with an alias made from the pattern.
Saving with it ticked makes the alias from the pattern when the node has none, when its
alias was typed, or when the pattern makes aliases again on every save. A made alias is
otherwise kept. The typed alias is not checked while the box is ticked.

### Managing patterns

`/admin/config/search/path/patterns` lists the content types' patterns and needs
`administer url aliases`.

| Action | Request |
| --- | --- |
| add a pattern for a content type | `GET`, `POST .../add` with `bundle`, `pattern`, and `regenerate` |
| edit a pattern | `GET`, `POST .../manage/<content type>` |
| delete a pattern | `GET`, `POST .../manage/<content type>/delete` |

A pattern starts with `/` and holds letters, digits, `/ _ . ~ -`, and tokens, in at most
255 characters, checked in the browser and on the server. Deleting a pattern leaves the
aliases made from it.

# Redirects

A redirect sends a request for a path elsewhere. Redirects are stored in the `redirect`
table: the source path, the destination, and the status. A source has one redirect.

`RedirectFilter` runs before the alias filter and routing. A request for a redirect's
source is answered with its status and a `Location` of its destination, with the request's
query string added when the destination has none. A destination that is a path of the
site is given the site's context path.

`RedirectManager.refusal(id, source, destination, status)` gives the reason a redirect is
refused, and `save` throws `IllegalArgumentException` with it:

- The source is written like an alias.
- The destination is a path of the site, such as `/about`, or an `http` or `https`
  address, in at most 2048 characters.
- The source and destination differ.
- The status is 301, 302, 303, 307, or 308.
- No other redirect has the source.

Lookups are kept in memory once made, and any change clears what the instance kept.

## Redirects from old aliases

`RedirectManager.moved(oldPath, newPath)` sends the old path to the new one with a 301,
points redirects that sent to the old path at the new one, so a request is never sent on
twice, and takes away a redirect from the new path. Changing an alias calls it with the
old and new alias, and taking an alias away with the old alias and the source. Saving an
alias also takes away any redirect from the alias's path, so the page answers there.

## Managing redirects

`/admin/config/search/redirect` lists the redirects and needs `administer redirects`.

| Action | Request |
| --- | --- |
| add a redirect | `GET`, `POST .../add` with `source`, `destination`, and `status` |
| edit a redirect | `GET`, `POST .../manage/<id>` |
| delete a redirect | `GET`, `POST .../manage/<id>/delete` |

The source and destination patterns and lengths are checked in the browser and on the
server. The rest is checked on the server, with each reason on its field.
