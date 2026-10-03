# Files

A managed file is a content entity of type `file`. Its row records where the file is
kept and what it is, and the file's bytes live in a scheme.

## The file entity

`FileEntityType` declares these base fields:

| Field | Holds |
| --- | --- |
| `uri` | `<scheme>://<path>`, such as `public://2026-10/hours.pdf` |
| `filemime` | the file's media type |
| `filesize` | its size in bytes |
| `owner` | the account that uploaded it |
| `status` | `true` once the file is permanent, `false` while it is temporary |
| `created`, `changed` | when it was stored and when its status last changed |

The entity's label is the file's name. `ManagedFile` reads the same values off a loaded
entity, with the scheme and path split out of the uri.

## Schemes

A `FileScheme` keeps file content under one name: it writes, reads, checks for, and
deletes a path, and says whether a browser may fetch a path directly. A module adds a
scheme, such as an object store, by registering a `FileScheme` bean, and
`FileSchemes.Registry` finds schemes by name.

Core registers two `LocalDiskScheme`s:

| Scheme | Directory | Served |
| --- | --- | --- |
| `public` | `springdrop.files.public-path`, `files/public` by default | as it is, at `/files/<path>` |
| `private` | `springdrop.files.private-path`, `files/private` by default | through access checks, at `/system/files/<path>` |

Relative directories are read from the directory the application runs in. A
`LocalDiskScheme` refuses an empty path and any path that resolves outside its
directory.

### Private files

`PrivateFileController` serves `/system/files/<path>`, the file whose uri is
`private://<path>`. A path naming no stored private file answers 404. `FileAccess`
decides who may download it:

- A temporary file goes only to the account that uploaded it, so a form can preview an
  upload before it is saved.
- A permanent file goes to anyone who may `view` a content entity that uses it, by that
  entity type's access handler. Usage by a config entity, or by an entity that is gone,
  grants nothing.

Anyone else gets 403. A served file carries its stored media type, an inline
`Content-Disposition` with its name, and a private `no-cache` `Cache-Control`.

## Storing a file

`FileService.store(content, filename, size, scheme, owner)` writes the content and
saves a temporary file entity:

- The file goes in a directory named for the month it arrived, such as `2026-10`.
- Its name keeps letters, digits, dots, dashes, and underscores, drops any directories
  it was sent with, and becomes `file` when nothing safe is left.
- A name already taken gets a number before its extension: `hours_1.pdf`, `hours_2.pdf`.
- A name ending in an extension in `FileService.DANGEROUS_EXTENSIONS`, one a server
  might run or a browser render as a page, gets `.txt` added.
- The media type comes from the stored name, never from what the sender claimed, and is
  `application/octet-stream` when the name says nothing.

A scheme the site does not have is refused. `find`, `findByUri`, and `read` load a file
and its content. `url(file)` is the scheme's direct address, or the `/system/files`
address for a scheme without one. `delete(id)` removes the content and then the entity,
and a file whose content cannot be removed is kept.

## Usage

`FileUsageService` counts what uses each file in the `file_usage` table, by module,
entity type, and entity id:

- `add(file, module, type, id)` counts one more use and makes the file permanent.
- `remove(file, module, type, id, everyUse)` counts one use fewer, or drops every use
  by that entity. A file nothing uses afterwards is temporary again.
- `total(file)`, `usages(file)`, and `filesUsedBy(module, type, id)` read the counts back.

## File and image fields

The `file` field type stores `target_id`, `description`, and `display` per value. The
`image` field type stores `target_id`, `alt`, `title`, `width`, and `height`. Both keep
uploads in the scheme the storage's `uri_scheme` setting names, `public` by default.

Their instance settings bound what may be attached:

| Setting | Field types | Holds |
| --- | --- | --- |
| `file_extensions` | both | allowed extensions separated by spaces: `txt` for files, `png gif jpg jpeg` for images |
| `max_filesize` | both | the largest file in bytes, 0 for no limit |
| `description_field` | `file` | whether the form asks for a description |
| `alt_field`, `alt_field_required` | `image` | whether the form asks for alt text, and whether it must be filled in |
| `title_field` | `image` | whether the form asks for a title |
| `min_resolution`, `max_resolution` | `image` | `<width>x<height>` bounds in pixels, blank for none |

The `file_item` constraint checks each value on save:

- the value points at a stored file,
- the stored name's extension is allowed and the file is within the size limit,
- for an image, the site can read it as an image (PNG, GIF, or JPEG), its pixel size is
  within the bounds, and it has alt text when that is required.

A save made in a request also has to be allowed to attach the file: the file is either
one the entity already uses or a temporary upload owned by the person saving. Uploads
made without signing in are owned by account 0. A save made outside a request, such as
an install step, skips this check.

`FileUsageTracker` keeps usage in step with these fields. After an entity is inserted or
updated, it records one use, under the module `file`, of every file any file or image
field of the entity points at, in any language and in any revision the entity keeps. It
releases files the entity no longer points at anywhere. Deleting the entity releases
every file it used. Deleting a single revision leaves usage as it is until the entity is
next saved.

The `file_default` formatter shows a file as a link to its URL, labeled with its
description or its name, followed by its size. A value whose `display` is `false`, or
whose file is gone, shows nothing. The `image` and `responsive_image` formatters are
described in [Image styles](image-styles.md).

## Uploading

`file_generic` and `image_image` are the widgets for file and image fields. Each edits
every value of its field in one fieldset:

- Each attached file is listed by name, with a preview for an image, the inputs the field
  asks for (a description, or alt text and a title), an Order number, and a Remove box.
  The values are submitted as `<field>[<n>]:target_id`, `<field>[<n>]:alt`, and so on,
  and read back in Order, leaving out removed files and files no longer stored.
- A file input uploads each picked file to `POST /file/upload` as soon as it is picked,
  with a progress bar, and adds the file to the list. It takes several files when the
  field holds more than one, and turns off once the field is full.

The image widget reads `width` and `height` from the stored image on save, never from
the form.

`/file/upload` takes `entity_type`, `bundle`, `field`, `delta` (the position the new file
gets in the form), and `file`. It answers:

| Status | When | Body |
| --- | --- | --- |
| 200 | the file was stored | the inputs for the new file |
| 403 | the person is not signed in | `Sign in to upload files.` |
| 404 | the field does not exist or is not a file or image field | a short reason |
| 422 | the file breaks the field's limits | the reason, such as `The file is larger than 4096 bytes.` |

A stored upload is a temporary file owned by the uploader, kept in the field's scheme, and
becomes permanent when the form is saved with it. A refused image is deleted at once.

The site's largest upload is `spring.servlet.multipart.max-file-size`, 32 MB. A field's
`max_filesize` above that, or 0, is held to the site's limit.

### The same checks in the browser

`UploadLimits` holds a field's limits and builds each refusal message. The widget writes
the limits and the messages onto the file input as `data-` attributes, and
`/js/file-upload.js` checks each picked file against them before sending it: the
extension of the name the server will store it under (with `.txt` added to the
extensions in `FileService.DANGEROUS_EXTENSIONS`), the size, and for an image, that the
browser can read it and its pixel size is within bounds. A refused file shows the
server's message and is not sent. The server checks every upload again, and the
`file_item` constraint checks the saved values a third time with the same messages.

## Removing temporary files

`TemporaryFileCollector` is a `CronJob` with the id `file.temporary`. It deletes every
temporary file that nothing uses once the file is older than
`springdrop.files.temporary-max-age`, six hours by default, so an upload can sit in a
form being filled in. A file marked temporary that something still uses is kept.

`CronRunner.run()` runs every `CronJob` bean in id order. A module adds housekeeping by
registering one.
