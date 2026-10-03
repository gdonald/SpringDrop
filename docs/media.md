# Media

A media item is a reusable image, document, audio file, video file, or remote video that
content refers to. Media is a fieldable, revisionable content entity of type `media`,
whose bundles are media types.

## The media entity

`MediaEntityType` declares `media` with the bundle key `bundle` and the bundle entity
type `media_type`. Its label is the item's name. Its base fields are the authored-content
ones (`status`, `created`, `changed`, `owner`) and `thumbnail`, the id of a managed image
file standing for the item. Its canonical link is `/media/{media}`.

## Media types

`MediaType` is a config entity stored as `media_type.<id>`: a label, a description, the
media source its items come from, and the source field holding each item's source value,
named `field_media_<type>`.

`MediaService.createType(id, label, description, source)` saves the type, creates its
source field as a required field of the type the source names, set up as the source
says, and shows it in the default view display through the source's formatter.
`deleteType` removes the type's fields, display, and config. A type with media is not
deleted.

## Media sources

A source is a plugin registered with `@SpringDropPlugin(type = MediaSource.class)`. It
names the source field's type and settings and its default formatter, and reads a source
value's default name and thumbnail through `metadata(value)`, throwing
`MediaSourceException` with the reason for a value it cannot use.

| Id | Label | Source field | Name | Thumbnail |
| --- | --- | --- | --- | --- |
| `image` | Image | `image`, png gif jpg jpeg, alt text required | the file's name | the image itself |
| `file` | Document | `file`, txt rtf pdf doc docx odt xls xlsx ods ppt pptx odp csv | the file's name | the document icon |
| `audio_file` | Audio file | `file`, mp3 wav aac ogg oga m4a | the file's name | the audio icon |
| `video_file` | Video file | `file`, mp4 webm ogv mov | the file's name | the video icon |
| `oembed:video` | Remote video | `string` of up to 2048 characters, the video's address | the provider's title, or the address | the provider's thumbnail, or the video icon |

`MediaIcons` draws each icon, a gray square naming its kind, the first time it is asked
for, stores it as a public managed file kept permanent by a use of its own, and reuses it
after that. It remembers each icon by its uri.

## Saving media

`MediaService.save(item, newRevision)` reads the source value through the type's source
when the item is new, its source value changed, it has no name, or it has no thumbnail.
The thumbnail always comes from the source then, and the name only when the item has
none. A name longer than 255 characters is cut to 255. A value the source refuses stops
the save with an `EntityValidationException` whose violation is bound to the source
field, so the form shows the reason on that field. A saved item is stamped with when it
was created and changed.

`MediaThumbnailTracker` records, under the module `media`, the thumbnail of every
revision of an item, so an older revision keeps its thumbnail. Deleting the item releases
them.

## Remote video

`oembed:video` takes YouTube (`https://www.youtube.com/watch?v=...`,
`https://youtu.be/...`) and Vimeo (`https://vimeo.com/<number>`) addresses.
`HttpOEmbedClient` asks the provider's oEmbed endpoint about the address, does not follow
redirects, gives up after ten seconds, and only downloads a thumbnail from the provider's
own thumbnail hosts (`i.ytimg.com`, `i.vimeocdn.com`), up to five megabytes. An address
that is not a video, from no provider the site embeds from, or that the provider cannot
describe, is refused. A module replaces the client by registering another `OEmbedClient`
bean marked primary.

The `oembed` formatter draws a remote video as a 16 by 9 frame of `/media/oembed`, which
serves the provider's embed markup alone, so it runs apart from the site's page. The
frame's address carries a hash signed for the video's address, and the endpoint answers
404 to an address without a valid hash or one the provider cannot describe. Its
`Content-Security-Policy` lets it frame https pages and run nothing else, and only the
site may frame it. What a provider says about an address is kept for a day.

## Access

`MediaAccessHandler` decides:

| Operation | Allowed to |
| --- | --- |
| view | anyone with `view media` for a published item, and its owner with `view own unpublished media` for an unpublished one |
| update | `edit any <type> media`, or `edit own <type> media` for the owner |
| delete | `delete any <type> media`, or `delete own <type> media` for the owner |
| create | `create <type> media`, asked about the type's id |

`administer media` allows everything. `MediaPermissions` builds the per-type permissions
from the types the site has. `administer media types` and `access media overview` are
declared in `media.permissions.yml`.

## Pages

| Path | Page | Needs |
| --- | --- | --- |
| `/admin/structure/media` | media types, each with Edit, Manage fields, and Delete buttons | `administer media types` |
| `/admin/structure/media/add` | add a type: name, description, and source, which cannot be changed later | `administer media types` |
| `/admin/structure/media/manage/<type>` | edit a type's name and description | `administer media types` |
| `/admin/structure/media/manage/<type>/delete` | delete a type with no media | `administer media types` |
| `/admin/structure/media/<type>/fields` | the type's fields, through the Field UI | `administer fields` |
| `/media/add` | the types the person may create media of | |
| `/media/add/<type>` | add an item: name, the type's fields, and Published | create access |
| `/media/<id>` | the item, with View, Edit, and Delete tabs as access allows | view access |
| `/media/<id>/edit` | edit an item, optionally as a new revision | update access |
| `/media/<id>/delete` | delete an item | delete access |
| `/admin/content/media` | every item, latest change first, with its thumbnail in the `thumbnail` image style, type, status, and an Edit button where allowed | `access media overview` |

A name left empty on the form is taken from the source.

## Media reference fields and the library

A media reference field is an `entity_reference` field whose storage's `target_type` is
`media`. Its instance's `target_bundles` setting names the media types it takes, and
naming none takes every type. Setting the instance's `widget` to `media_library` picks
media from the library.

The `media_library` widget lists the chosen media, each with its thumbnail in the
`thumbnail` image style (or the original when the site has no such style), an Order
number, and a Remove box, submitted as `<field>[<n>]:target_id`, `:weight`, and `:remove`.
On save it keeps, in Order, the chosen media that exist and that the person may view, each
once. An Add media button opens the library, and is marked unavailable once the field
holds as many items as its cardinality allows.

`/media-library?entity_type=<type>&bundle=<bundle>&field=<field>` answers with the
library for that field, or 404 for a field that is not a media reference or takes no
media type the site has:

- a tab per media type the field takes, `type` choosing one, the first by default,
- a search by name, `q`,
- up to 24 items of the type the person may view, latest change first, each with a box to
  choose it and the widget's inputs for it, with `__delta__` where its position goes,
- for someone who may create media of the type, a form adding one: a file for the
  file-based sources, held to the type's source field limits and the site's largest
  upload, or an address for remote video, and an optional name.

`POST /media-library/add` creates the item as the person's own, answering with it as a
chosen library item, or with the reason it was refused:

| Status | When |
| --- | --- |
| 403 | the type is not one the field takes, the person is not signed in, or may not create media of the type |
| 422 | no file was sent, the file breaks the limits, the name is longer than 255 characters, or the source refuses the value |

A refused upload's file is not kept. An image's alternative text is its name, or its
file's name.

`/js/media-library.js` opens the library in a dialog, follows its tabs and search, sends
its form, and on Insert selected puts the chosen media into the field at the next
positions, as many as there is room for, leaving out media the field already holds. The
chosen media can be dragged into order or removed. Without JavaScript the button opens
the library as a page in a new tab.

## Embedding media in text

The `media_embed` text filter draws media embedded in formatted text as

```html
<media-embed data-entity-uuid="2b717e54-90fc-47ee-8b32-4b13cdc1716c" data-view-mode="default"></media-embed>
```

The item with that uuid is drawn in the named view mode, `default` when none is named.
A media item's edit form shows its embed code. Basic HTML and Full HTML run the filter
first, at weight -100, so no other filter removes the element before it is read.

The sanitizer would take the drawn item apart, so the filter swaps each embed for a
marker before the sanitizer runs and draws the item into it after. Each marker holds a
secret made fresh when the site starts, so text typed to look like a marker is left as
text. An item that is gone, or that the reader may not view, draws nothing, and an embed
without a uuid is removed. Media embedding media is drawn at most three levels deep, so
an item embedding itself ends.
