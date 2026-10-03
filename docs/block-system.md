# Block system

A block is a piece of a page that is not the page's own content: the site name, a menu,
the breadcrumb, a notice. Blocks are placed in the regions of a theme, and each placement
decides where the block sits and when it shows.

## Block plugins

A kind of block is a plugin registered with
`@SpringDropPlugin(type = BlockPlugin.class)`. It names itself for the block library,
builds what it shows, says what that depends on, and describes the settings a placement
of it carries:

```java
@SpringDropPlugin(id = "trip_count", type = BlockPlugin.class)
class TripCountBlock implements BlockPlugin {

    public String label() {
        return "Trip count";
    }

    public Optional<Renderable> build(BlockContext context, Map<String, Object> settings) {
        return Optional.of(Renderable.of("text").with("value", trips.count() + " trips"));
    }

    public CacheMetadata cacheability(Map<String, Object> settings) {
        return CacheMetadata.EMPTY.withTag("trip_list");
    }
}
```

`BlockContext` carries the page the block is built for: its path, its title, the main
content its controller produced, and the entity the route is about, when there is one.
A block with nothing to show on a page returns an empty `Optional`, and its placement is
left off that page, title and all.

A block with settings returns the elements they are edited with from `settingsForm`,
reads a submission back with `settingsValues`, and checks what the element rules cannot
in `validateSettings`. Settings elements are named with `BlockPlugin.SETTINGS_PREFIX`, so
they never share a name with the placement's own fields.

`BlockManager` lists the plugins by label and hands one out by id.

### The blocks core ships

| Id | Label | Shows |
| --- | --- | --- |
| `system_branding_block` | Site branding | the site name, linked home, and the slogan |
| `page_title_block` | Page title | the title of the page |
| `system_menu_block:<menu>` | the menu's label | one menu as a nested list, with the active trail marked |
| `system_breadcrumb_block` | Breadcrumbs | the trail to the page |
| `system_main_block` | Main page content | the content the page's controller produced |
| `help_block` | Help | the help a module gives for the page |
| `system_powered_by_block` | Powered by SpringDrop | a line naming the platform |
| `custom_html` | Custom HTML | markup written into the placement |
| `block_content` | Custom block | a custom block from the library |
| `field_block:<type>:<bundle>:<field>` | the field's label | one field of the entity the page is about, in layouts only |
| `user_account_block` | Account | "Signed in as" the account's name with a Log out button, or a Log in link, built for each request through the `account_greeting` placeholder |

The menu block is derived: `MenuBlockDeriver` makes one per menu, and
`system_menu_block:main` is the primary menu. Saving or deleting a menu drops the cached
plugins, so a new menu has a block as soon as it exists.

Custom HTML is cleaned against jsoup's relaxed list of safe elements as it is drawn, so a
script, a style, or an event handler written into it never reaches the page.

Help comes from `PageHelp` beans. A module registers one and answers for its own paths:

```java
@Component
class TripHelp implements PageHelp {

    public Optional<String> helpFor(String path) {
        return path.startsWith("/trips") ? Optional.of("Trips are listed newest first.") : Optional.empty();
    }
}
```

## Regions

A theme declares the regions blocks are placed in, and a theme that declares none uses
its parent's:

```java
Theme.named("vista").withRegions(List.of(
        new Region("header", "Header"),
        new Region(Region.CONTENT, "Content"),
        new Region("sidebar", "Sidebar")));
```

`front-end` has `header`, `primary_menu`, `breadcrumb`, `highlighted`, `help`,
`content`, `sidebar`, and `footer`. `admin` has `header`, `breadcrumb`, `highlighted`,
`help`, and `content`.

Each region is drawn into the layout's `slots` under its id, so a layout places a region
with `<th:block th:utext="${slots['sidebar']}"></th:block>`. A region with no blocks
showing is not in `slots` at all, and the front-end layout draws its sidebar column only
when the sidebar has something in it.

## Placements

A placement is a config entity named `block.block.<id>`: the plugin, the theme and
region, the title and whether it shows, the weight, the plugin's settings, and the
visibility conditions. `BlockPlacementManager` reads and writes them:

```java
placements.save(BlockPlacement.of("front_end_notice", "front-end", "sidebar", "custom_html", "Notice")
        .withSettings(Map.of("body", "<p>Closed Monday.</p>"))
        .withWeight(-5));
```

Within a region, blocks come out lightest first, then by title.

### Visibility conditions

A condition is a plugin registered with
`@SpringDropPlugin(type = VisibilityCondition.class)`. A placement shows only when every
condition it carries holds, and a negated condition shows the block everywhere except
where it holds. A condition whose plugin is gone hides the block.

| Id | Label | Holds when |
| --- | --- | --- |
| `request_path` | Pages | the path matches one of the listed patterns. `*` matches anything, and `<front>` is the front page. |
| `user_role` | Roles | the visitor holds one of the chosen roles |
| `entity_bundle:<entity type>` | Type of the entity type | the page is about an entity of one of the chosen bundles |
| `language` | Language | the page is shown in one of the listed languages |

The bundle condition is derived per entity type that has bundles. The one for nodes is
the content type condition.

The role condition reads roles from the signed-in account's authorities. An account
carries each role it holds as a `ROLE_<role>` authority beside its permissions, everyone
signed in holds `authenticated`, and a visitor who has not signed in holds `anonymous`
alone.

Each condition reports the cache context its decision varies by: `url.path`,
`user.roles`, `route`, or `languages`. The page carries those contexts whether the block
showed or not, since a different visitor may get a different answer.

## Drawing a page with its blocks

`BlockPageRenderer` builds the regions of the active theme and draws the page around
them:

```java
return blockPages.render(chrome, content, BlockContext.of(path, title)).html();
```

Each block is drawn through the themed `block` template, with suggestions from most
specific to least: `block--<placement id>`, `block--<plugin>--<derivative>`,
`block--<plugin>`, and `block`. The block carries its placement's tag,
`config:block.block.<id>`, and its plugin's cacheability, and every page with blocks
carries `config:block_list`, which saving or deleting any placement invalidates.

When a main content block is placed and shows, the page's content is drawn in its region
and nowhere else. When none is placed, the layout draws the content where it always has.

## Administering blocks

Everything here sits behind the `administer blocks` permission.

`/admin/structure/block` shows the block layout of the front-end theme, and
`/admin/structure/block/list/<theme>` any other theme's, with a tab per theme. Each
region lists its blocks with a region choice and a weight input, which submit with or
without JavaScript. With JavaScript, `drag-order.js` lets the rows of a region be dragged
and renumbers the weights to match.

**Place block** opens the library of block plugins for that region, and placing one opens
a form for its title, region, weight, the plugin's settings, and a section per visibility
condition. The same form edits a placed block at `/admin/structure/block/manage/<id>`.

## Custom blocks

A custom block is content: the `block_content` entity type, revisionable and fieldable,
in block types that are bundles of it. `BlockContentService` reads and writes them, and
its `install()` creates their tables.

`/admin/structure/block-content` lists the block types and adds one. Each type's fields
are managed through the Field UI at `/admin/structure/block_content/<type>/fields`. A
type that blocks in the library are still written in cannot be deleted.

`/admin/content/block` is the library. A block is written with a description, which only
the library shows, and the fields its type carries, and every save keeps a revision. A
custom block is placed through the `block_content` plugin, whose one setting names the
block, so one custom block can be placed in several themes and regions and edited once.
It is drawn through its type's `full` view display.
