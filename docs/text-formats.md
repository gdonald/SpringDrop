# Text formats

A text format decides what text written in it becomes on the page, and who may write
in it.

## Formats

A format is the config entity `filter.format.<id>`, read and written through
`TextFormatManager`. `TextFormat` holds its label, weight, whether it is the fallback
format, and the filters it runs, each a `FilterConfig` of plugin id, weight, and settings.
Formats are listed lightest first.

The application adds four formats when it starts, each only when the site lacks it:

| Format | Filters | Keeps |
| --- | --- | --- |
| Plain text, the fallback | show HTML as text, link addresses, convert line breaks | no markup of its own |
| Restricted HTML | allowed HTML, convert line breaks, link addresses, correct faulty HTML | the tags of structured writing |
| Basic HTML | embed media, allowed HTML, correct faulty HTML | those tags plus `<span>` and `<img>` |
| Full HTML | embed media, correct faulty HTML | the broad set of content tags |

## Who may use a format

Every person may write in the fallback format. Any other format is gated by
`use text format <id>`, which `TextFormatPermissions` builds from the formats the site
has, so a format's roles are the roles granted that permission. A format without the
allowed-HTML filter lets its writers put any content markup on the page, so its
permission is restricted. `administer filters` and the first account may use every
format. `formatsFor(authentication)` lists the formats someone may use.
`DefaultTextFormatAccess.grant()`, which an install profile calls, gives Restricted HTML
to the anonymous role and Basic HTML to the authenticated role.

## Filters

A filter is a plugin registered with `@SpringDropPlugin(type = TextFilter.class)`. It
names itself, processes text with its settings, and may offer the elements its settings
are edited with.

| Id | Does |
| --- | --- |
| `filter_html` | keeps only the tags and attributes its `allowed_html` setting names |
| `filter_autop` | turns blank lines into paragraphs and single line breaks into `<br>` |
| `filter_url` | links web and mail addresses, showing at most `length` characters of each |
| `filter_htmlcorrector` | closes open tags and drops stray closing ones |
| `filter_html_escape` | shows every tag as written |
| `filter_markdown` | renders CommonMark, dropping links to anything but web and mail addresses |
| `media_embed` | draws embedded media items, see [Media](media.md#embedding-media-in-text) |

`allowed_html` names each tag in angle brackets with the attributes it may carry, such as
`<a href hreflang> <em>`. `AllowedHtml` reads it. Scripts, styles, frames, objects, forms,
SVG, and MathML are never kept, nor any event handler or style attribute, whatever the
setting says.

## Rendering

`TextFormatManager.process(text, formatId)` runs the text through the format's filters in
weight order, passing over a filter the site no longer has, then through `HtmlSanitizer`,
the OWASP HTML Sanitizer. The sanitizer keeps the tags the format's allowed-HTML filter
names, or the broad set of content tags for a format without one, and keeps links and
images only to http, https, and mailto addresses. It runs on every render, so stored text
and whatever an editor in the browser let through are cleaned again. After the sanitizer,
each filter's `afterSanitizing` step runs in the same order, for a filter whose output the
sanitizer would remove, such as media the site draws itself. Most filters leave that step
alone. Text in a format the
site no longer has is processed by the fallback format.

`allowedHtml(format)` is the set of tags the format keeps. `editorTags(format)` is the set
an editor offers: the same tags, or none for a format running `filter_html_escape`, such as
Plain text, which shows markup as text. A format running `media_embed` adds `media-embed`,
so the editor keeps embeds in the text.

## Editor

`/js/editor.js` puts a rich-text editor over each formatted-text textarea on entity forms.
The textarea stays the form's control: the editor hides it, writes into it as the text
changes and when the form is sent, and with JavaScript off the textarea is what the
person writes in. The editor follows the format choice. A format with no editor tags
shows the textarea again.

The toolbar offers a button only when the format keeps every tag it writes:

| Button | Tags |
| --- | --- |
| Bold, Italic, Underline, Strikethrough, Code, Subscript, Superscript | `strong`, `em`, `u`, `s`, `code`, `sub`, `sup` |
| Link | `a`, for http, https, mailto, same-site, and `#` addresses |
| Heading 2 to Heading 6, Quote | `h2` to `h6`, `blockquote` |
| Bulleted list, Numbered list | `ul` and `li`, `ol` and `li` |
| Image | `img` |

Pressing a heading, quote, or list button on a block of that kind turns it back into a
paragraph, and a list button on the other kind of list switches the list's kind. Text
loaded into the editor keeps only the format's tags plus `p` and `br`, and loses script
elements, event-handler and style attributes, and addresses that are not web, mail, or
same-site ones. The server sanitizes again on every render.

### Images in text

The Image button picks an image, checks its kind and size against the limits the textarea
carries, with the server's messages, and sends it to `POST /editor/upload` with the
format. `EditorUploadController` answers:

| Status | When | Body |
| --- | --- | --- |
| 200 | the image was stored | JSON with `id`, `url`, `width`, and `height` |
| 403 | the person is not signed in, may not use the format, or the format does not keep `img` | the reason |
| 422 | the file is not a PNG, GIF, or JPEG, is larger than the site's largest upload, or is not an image | the reason |

The image is stored as a public temporary file owned by the uploader, and inserted at the
caret as `<img src alt width height data-file-id>`, asking for its alternative text. The
sanitizer drops `data-file-id` on render. `FileUsageTracker` records, under the module
`editor`, every public file a formatted text field's text or summary names with
`data-file-id`, so a saved image is permanent and is released once no revision's text
names it. A private file named this way is not counted.

## Admin

`/admin/config/content/formats` lists the formats with the roles that may use each, an
Edit button, and a Delete button for any but the fallback, and requires
`administer filters`. A format's form sets its name, weight, roles, and for each filter
the site has whether the format runs it, its weight, and its settings. The fallback has no
roles to choose. Deleting a format takes its permission from every role, and text written
in it is then shown the way the fallback shows text.
