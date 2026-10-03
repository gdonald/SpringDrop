# Image styles

An image style is a named way of drawing images, such as a thumbnail. It holds a list of
effects that are applied to the original in weight order, lightest first. A copy drawn
with a style is a derivative. The original is never changed.

## Styles

`ImageStyle` is a config entity stored as `image.style.<id>`. It has a label and a list of
`EffectConfig`s: an id unique within the style, the effect plugin, a weight, and the
plugin's settings. `ImageStyleManager` finds, lists, saves, and deletes styles.

A site starts with these styles, added at startup when missing:

| Id | Effect |
| --- | --- |
| `thumbnail` | Scale to fit 100 by 100 |
| `medium` | Scale to fit 220 by 220 |
| `large` | Scale to fit 480 by 480 |
| `wide` | Scale to 1090 wide |
| `square` | Scale and crop to 300 by 300 |

None of them upscale.

## Effects

An effect is a plugin registered with `@SpringDropPlugin(type = ImageEffect.class)`. It
applies itself through the site's `ImageToolkit`, and reports the size an image of a given
size comes out at without drawing it, which `ImageStyleManager.transformedSize` chains
through a style. It can also offer a settings form, read a submission of it, and
summarize its settings for the style's list.

| Id | Effect | Settings |
| --- | --- | --- |
| `image_scale` | fits the image inside a box, keeping its proportions | `width`, `height` (at least one), `upscale` |
| `image_scale_and_crop` | scales the image to cover a box, then crops the overhang evenly | `width`, `height` |
| `image_crop` | cuts a box out of the image without scaling it | `width`, `height`, `anchor` |
| `image_resize` | stretches the image to an exact size | `width`, `height` |
| `image_desaturate` | turns the image to shades of gray | none |
| `image_rotate` | turns the image clockwise, growing it to fit | `degrees` (-360 to 360), `background` (`#rrggbb` or blank) |

Sizes are whole pixels from 1 to 99999. The crop anchor is one of `left`, `center`, or
`right`, then `-`, then one of `top`, `center`, or `bottom`, such as `center-center`. A
crop larger than the image, or the corners a rotation by other than a right angle
uncovers without a background, come out transparent.

An effect whose plugin the site no longer has is skipped.

## The toolkit

`ImageToolkit` loads and saves images and offers the operations effects are built from:
resize, crop, desaturate, and rotate. `Java2dToolkit` is core's, using Java 2D and
ImageIO. It reads and writes PNG, GIF, and JPEG, and draws an image saved as JPEG over
white, since JPEG has no transparency. A module replaces the toolkit by registering
another `ImageToolkit` bean marked primary.

## Derivatives

A derivative of `<scheme>://<path>` drawn with a style is kept in the same scheme at
`styles/<style>/<scheme>/<path>`, so a private image's derivatives are as private as the
image.

`ImageStyleManager.url(style, file)` is where a derivative is fetched:

- `/files/styles/<style>/public/<path>?itok=<token>` for a public image, served to anyone
  with a public `Cache-Control` and a one-day `max-age`.
- `/system/files/styles/<style>/private/<path>?itok=<token>` for a private image, served to
  whoever `FileAccess` lets download the original, with a private `no-cache` `Cache-Control`.

The first request draws the derivative and keeps it. Later requests serve the kept copy
until the original is newer, when it is drawn again. Drawing takes work, so the `itok`
token is signed for the style and the original, and a request without a valid token is
served only a derivative that is already drawn and current. Without one it answers 403.

A style the site does not have, a path naming no stored file, or an address whose scheme
does not match its prefix answers 404, and so does an original that is not an image the
toolkit reads.

Saving or deleting a style deletes every derivative drawn with it, so the next request
draws it the new way. Deleting a file deletes its derivatives of every style.

## Managing styles

`/admin/config/media/image-styles` lists the styles with their effects and needs
`administer image styles`.

| Action | Request |
| --- | --- |
| add a style | `GET`, `POST .../add` with `label` |
| rename a style and reorder its effects | `POST .../manage/<style>` with `label` and `weight_<effect>` |
| add an effect | `POST .../manage/<style>` with `new_effect` and `add_effect` |
| set a new effect's settings | `GET`, `POST .../manage/<style>/add/<plugin>` |
| edit an effect's settings | `GET`, `POST .../manage/<style>/effects/<effect>` |
| remove an effect | `GET`, `POST .../manage/<style>/effects/<effect>/delete` |
| flush the derivatives | `GET`, `POST .../manage/<style>/flush` |
| delete a style | `GET`, `POST .../manage/<style>/delete` |

Adding an effect without settings, such as Desaturate, puts it straight into the style.
Any other goes on to its settings form. A new effect goes after the others.

## Image formatters

The `image` formatter draws an image field's value as an `img` with the alt text, the title
when there is one, and the stored width and height carried through the style's effects.
Its settings, edited on Manage display:

| Setting | Holds |
| --- | --- |
| `image_style` | the image style to draw with, blank for the original |
| `image_link` | blank for no link, `content` for the entity the field belongs to, `file` for the original file |
| `image_loading` | `lazy` (the default) or `eager` |

A style the site no longer has draws the original. A link to the content needs the entity
being rendered, and its type's canonical link.

## Responsive images

### Breakpoints

A `Breakpoint` is a media query a theme lays pages out for, with a weight ordering it
narrowest first and the pixel densities it is drawn at. A `BreakpointGroup` bean holds one
theme's breakpoints under the theme's name, and `BreakpointManager` lists the groups. The
front-end theme declares Bootstrap's:

| Id | Media query |
| --- | --- |
| `front-end.xs` | `all` |
| `front-end.sm` | `(min-width: 576px)` |
| `front-end.md` | `(min-width: 768px)` |
| `front-end.lg` | `(min-width: 992px)` |
| `front-end.xl` | `(min-width: 1200px)` |
| `front-end.xxl` | `(min-width: 1400px)` |

Each is drawn at `1x` and `2x`.

### Responsive image styles

`ResponsiveImageStyle` is a config entity stored as `responsive_image.styles.<id>`. It
names a breakpoint group, a fallback image style, and a list of `ResponsiveImageMapping`s.
A mapping is for one breakpoint and density, and is either:

- `image_style`: one image style, or `_original` for the original image, or
- `sizes` (at `1x` only): several image styles and a `sizes` attribute, so the browser
  picks by the width each style draws at.

`ResponsiveImageStyleManager.render` draws an image as a `picture`:

- one `source` per breakpoint with mappings, widest first, since a browser takes the
  first source whose media query matches. The `all` breakpoint's source has no `media`.
- an `image_style` source lists each density's address with its density, such as
  `.../narrow/... 1x, .../wide/... 2x`.
- a `sizes` source lists each style's address with the width it draws the image at, such
  as `... 400w, ... 800w`, and carries the `sizes`. A style whose width cannot be worked
  out, such as for an image of unknown size, is left out of the list.
- the `img` draws the fallback style with its size, and the alt text, title, and loading.

Mappings to image styles the site no longer has are left out, and a breakpoint left
with nothing has no source.

The `responsive_image` formatter draws an image field with a responsive image style, and
takes the same `image_link` and `image_loading` settings as `image`, with
`responsive_image_style` in place of `image_style`. Without a responsive image style the
site has, it draws the original as a plain `img`.

### Managing responsive image styles

`/admin/config/media/responsive-image-style` lists them with their breakpoint group and
needs `administer responsive images`. Adding one asks for a name, a breakpoint group, and
a fallback image style. Its edit page has a section per breakpoint and density, each with
a type, the image style for `image_style`, and for `sizes` the sizes and a box per image
style. The sizes are required for a `sizes` mapping, and a `sizes` mapping with no image
style ticked is left out. Deleting asks first.
