# Layout Builder

Layout Builder arranges blocks in sections. A section is one row of a layout: it names
the layout plugin it is built from, the column widths it splits that layout's regions
by, and the blocks placed in each region.

## Layout plugins

A layout is a plugin registered with `@SpringDropPlugin(type = LayoutPlugin.class)`. It
names itself, lists its regions, and lists the column widths it offers:

```java
@SpringDropPlugin(id = "layout_sidebar", type = LayoutPlugin.class)
class SidebarLayout implements LayoutPlugin {

    public String label() {
        return "Sidebar";
    }

    public List<LayoutRegion> regions() {
        return List.of(new LayoutRegion("main", "Main"), new LayoutRegion("aside", "Aside"));
    }

    public List<String> columnWidths() {
        return List.of("75-25", "67-33");
    }
}
```

Widths are each region's share of the row in percent, in region order, joined by `-`.
The first width listed is the one a section uses until it picks another, and a width the
layout does not offer falls back to that first one. A layout with one region lists none,
and its region spans the whole row.

`LayoutManager` lists the plugins by label and hands one out by id.

### The layouts core ships

| Id | Label | Regions | Widths |
| --- | --- | --- | --- |
| `layout_onecol` | One column | `content` | none |
| `layout_twocol_section` | Two column | `first`, `second` | `50-50`, `33-67`, `67-33`, `25-75`, `75-25` |
| `layout_threecol_section` | Three column | `first`, `second`, `third` | `33-34-33`, `25-50-25`, `25-25-50`, `50-25-25` |

## Sections

`Section` holds the layout id, the chosen widths (blank for the layout's first), and a
list of `SectionComponent`s. A component is a region, a weight, and a `BlockInstance`:
the block plugin, its label, whether the label shows, and the plugin's settings. A theme
placement carries the same `BlockInstance`, so any block plugin can be placed in a
section.

```java
Section section = Section.of(TwoColumnLayout.ID)
        .withColumnWidths("33-67")
        .withComponent(new SectionComponent(TwoColumnLayout.FIRST, 0,
                BlockInstance.of("fares", CustomHtmlBlock.ID, "Fares")));
```

Sections are records, so they save to and read from the config store as they are.

## Rendering

`SectionRenderer` draws a list of sections in order. Each section is drawn through the
`section` template, with suggestions `section--<layout id>` then `section`, so a theme
overrides one layout's markup without touching the others. The template puts each region
in a Bootstrap column, `col-12` on small screens and `col-md-<n>` from medium up, where
`n` is the region's share of the 12-column grid.

Blocks in a region are drawn lightest first through the block template, and their
cacheability bubbles to the page as it does for blocks placed in a theme. The renderer
leaves out:

- a section whose layout plugin the site no longer has,
- a block in a region its section's layout does not have,
- a block whose plugin the site no longer has,
- a block whose plugin builds nothing for the page.

An empty region keeps its column, so the widths of the regions around it hold.

## Layouts for a bundle's view mode

A view mode of a bundle can be drawn by Layout Builder instead of by its view display.
`LayoutBuilderDisplay` records whether it is, and the sections it is drawn with, as the
config entity `layout_builder.display.<entity type>.<bundle>.<mode>`.
`LayoutDisplayManager` reads and saves it:

- `enable` turns Layout Builder on. A view mode that has never had a layout starts with
  one single-column section holding a field block for each field the view display shows,
  in the same order, through the same formatters, and with the same labels shown, so the
  entity reads as before.
- `disable` turns it off and keeps the sections, so turning it back on restores them.
- `render(entity, mode, context)` draws the entity through its bundle's layout for that
  view mode, or returns nothing when Layout Builder does not draw it. What it draws
  carries the layout's `config:` cache tag.

`NodeService.build` asks for the layout first and falls back to the view display.

### Field blocks

`FieldBlockDeriver` derives a block for every field on every bundle, as
`field_block:<entity type>:<bundle>:<field>`. A field block draws that field of the
entity the page is about, through the formatter its settings name, with the field's
label left off, since the block's title can show it. It shows nothing on a page about no
entity, about an entity of another bundle, or when the field is empty. Adding or removing
a field drops the cached block plugins, so a new field has a block at once.

Field blocks belong to layouts: `BlockManager.definitions()`, the theme's block library,
leaves them out, and the block layout refuses to place one in a theme region.
`BlockManager.layoutDefinitions(entityType, bundle)` lists the theme's blocks and the
field blocks of that bundle.

### The layout editor

`/admin/structure/<entity type>/<bundle>/display/<mode>/layout` edits one layout, linked
from Manage display as Manage layout. It requires `administer fields`. `LayoutEditor`
holds the editing itself, so a bundle's layout and one entity's layout are edited the
same way.

| Action | Request |
| --- | --- |
| turn Layout Builder on or off | `POST .../layout/enable`, `POST .../layout/disable` |
| save widths, regions, and weights | `POST .../layout` |
| add a section of a layout | `POST .../layout/section/add` with `layout` |
| remove a section and its blocks | `POST .../layout/section/<index>/remove` |
| choose a block for a region | `GET .../layout/section/<index>/region/<region>/library` |
| add a block | `POST .../layout/section/<index>/region/<region>/add/<plugin>` |
| configure a block | `POST .../layout/block/<id>` |
| remove a block | `POST .../layout/block/<id>/remove` |

The editor lists each section with its column-width choice and a table per region. Rows
drag into order and renumber their weights, and each row's region choice moves a block to
another region of its section. Widths the layout does not offer, a region the section
does not have, and a weight that is not a whole number leave that part as it was. A new
block goes after the blocks already in its region. A field block starts with its title
hidden. A block whose plugin the site no longer has is marked missing and can be removed
but not configured.

## Layouts of a single entity

A bundle's layout can let each of its entities have a layout of its own, through the
"Let each piece of content have a layout of its own" choice on its editor. Only an entity
type that declares the `layout_builder__layout` base field
(`LayoutDisplayManager.OVERRIDE_FIELD`, a `jsonb` column) can allow it, and nodes do.

The override is a list of sections stored on the entity. Because it is a base field, it is
written to the entity's revision table on every save, so each revision keeps the layout it
had and loading an earlier revision draws that revision's layout. An entity has one
override shared by all of its translations. An empty list means the entity uses its
bundle's layout, and a bundle that stops allowing overrides draws its own layout for every
entity while each stored override stays where it is.

`/node/<id>/layout` edits one node's layout, and the node's page shows it as a Layout tab.
The editor starts from the content type's full layout, and the first change saves a copy
on the node. Every change saves the node as a new revision. Revert to the content type's
layout empties the override. `NodeLayoutAccess` decides who may: the content type's full
view mode has to allow overrides, and the person has to be allowed to edit the node and
hold `configure layout overrides`.
