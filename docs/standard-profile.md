# Standard profile

The `standard` install profile, `StandardProfile`, is the starting point most sites choose.
Its config is under `src/main/resources/springdrop/profiles/standard/config`, and
`StandardProfileIntegrationTest` installs it and checks the site against this inventory.

## Content types

| Content type | Machine name | Starts | Fields |
| --- | --- | --- | --- |
| Article | `article` | published, promoted to the front page | Body (`body`), Image (`field_image`), Tags (`field_tags`), Comments (`comment`) |
| Basic page | `page` | published, not promoted | Body (`body`) |

Every save of either makes a new revision.

## Fields

| Field | Storage | Type | On |
| --- | --- | --- | --- |
| Body | `field.storage.node.body` | `text_with_summary`, one value | Article, Basic page |
| Image | `field.storage.node.field_image` | `image`, one value, png, gif, jpg, or jpeg, alternative text required | Article |
| Tags | `field.storage.node.field_tags` | `entity_reference` to taxonomy terms of Tags, any number, new terms made as they are typed | Article |
| Comments | `field.storage.node.comment` | `comment`, threaded, preview optional | Article |

## Displays

| Display | Fields, in order |
| --- | --- |
| Article form | Body (textarea with summary), Image, Tags (autocomplete), Comments |
| Article full | Image (Wide image style), Body, Tags (labels), Comments |
| Article teaser | Image (Medium image style), Body (summary or trimmed), Tags |
| Basic page form | Body (textarea with summary) |
| Basic page full | Body |
| Basic page teaser | Body (summary or trimmed) |

## Taxonomy

| Vocabulary | Machine name |
| --- | --- |
| Tags | `tags` |

## Menus

Main navigation (`main`), Administration (`admin`), Footer (`footer`), and User account menu
(`account`).

## Blocks

Placed in the `front-end` theme:

| Block | Region |
| --- | --- |
| Main page content | Content |
| Help | Help |
| User account menu | Header |
| Footer menu | Footer |
| Powered by SpringDrop | Footer |

The layout draws the site name, the main menu, and the breadcrumb itself.

## Text formats and editor

Core's Plain text, Restricted HTML, Basic HTML, and Full HTML formats. The editor follows the
format chosen. Anonymous visitors write in Restricted HTML and signed-in visitors in Basic
HTML.

## Views

Core's content and people admin views, at `/admin/content` and `/admin/people`, with the
front page, files, taxonomy term, and search views.

## Roles

| Role | Permissions |
| --- | --- |
| Anonymous visitor (`anonymous`) | access content, access comments, use text format restricted_html |
| Signed-in visitor (`authenticated`) | access content, access comments, post comments, skip comment approval, edit own comments, use text format basic_html |
| Content editor (`content_editor`) | access content overview, view own unpublished content, and for Article and Basic page: create, edit own, delete own, view revisions, and revert revisions |
| Administrator (`administrator`) | every permission |

## Workflows

The Editorial workflow (Draft, Published, Archived) is created and moderates no content type.
