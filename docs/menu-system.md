# Menu system

A menu is a named tree of links. The tree is built per request, because what it holds
depends on who is asking.

## Menus

A menu is a config object under `system.menu.<id>`, read and written through
`MenuManager`:

```java
menus.save(MenuConfig.of("travel", "Travel", "Where the trips are written up."));
```

`MenuManager.install()` creates the four menus a site navigates by, each locked so that
it cannot be deleted while core links hang in it:

| Id | Label |
| --- | --- |
| `main` | Main navigation |
| `admin` | Administration |
| `footer` | Footer |
| `account` | User account menu |

Installing again leaves whatever is there alone, so renaming a menu survives a
reinstall. `MenuManager.delete` refuses a locked menu and says so by returning false.

## Links

A link is a `MenuLink` wherever it came from: its id, the menu it hangs in, its title
and description, where it goes, the id of its parent, its weight, whether it shows its
children unopened, whether it is on, and the permission it needs.

A module declares its links in code by registering a `MenuLinkProvider`, the same way it
registers its routes:

```java
@Component
class TravelMenuLinks implements MenuLinkProvider {

    @Override
    public List<MenuLink> menuLinks() {
        return List.of(
                MenuLink.of("travel.trips", MenuConfig.MAIN, "Trips", "/trips")
                        .withWeight(-5)
                        .requiring("read the trips"));
    }
}
```

A site's own links are content: the `menu_link_content` entity type, written through
`MenuLinkContentService`. Their ids carry the entity type as a prefix
(`menu_link_content:7`), which keeps them apart from the ids modules choose. The
service's `install()` creates the table, the way `UserAccountService.install()` creates
the account table.

## Building a tree

`MenuTreeBuilder.build(menuId, activePath)` gathers both sources, drops what the person
may not reach, nests what is left, and marks the trail to the page being shown:

```java
MenuTree tree = trees.build(MenuConfig.MAIN, "/trips/iceland");
```

A link is dropped when it is off, when its own required permission is not held, or when
the route it points at requires a permission that is not held. Dropping a link drops
everything under it, since a branch whose root is out of reach leads nowhere. The first
account bypasses these checks as it does everywhere else.

Two odd shapes resolve rather than fail. A link whose parent is not in the menu hangs at
the top, so a link outliving the one it hung under stays reachable. A link that is its
own ancestor hangs at the top too, since a ring of links has no top of its own.

Within each level, links come out by weight and then by title, so two links of equal
weight come out in a stable order.

The tree carries its own cacheability: the tag `menu:<id>`, which invalidates it, and
the `user.permissions` context, which it varies by.

`MenuTreeBuilder.buildForAdministration` skips the filtering. That is the admin's view
of a menu, where a link has to be visible to be turned back on.

## Drawing a menu

`MenuNavigation` turns a tree into the `Link` list the page chrome carries, so a theme
draws a menu without knowing how menus are stored:

```java
PageChrome chrome = PageChrome.of(site.name(), title)
        .withPrimaryNavigation(navigation.primary(path))
        .withBreadcrumbs(breadcrumbs.build(path));
```

Each `Link` carries its children and whether it lies on the active trail. The `menu`
partial draws a top-level link with children as a Bootstrap dropdown, and marks the
active one.

## Breadcrumbs

`BreadcrumbBuilder.build(path)` gives the trail to a page. A page that hangs in a menu
takes its trail from that menu, so the breadcrumb and the open menu branch agree: admin
paths are looked up in the administration menu and everything else in the main menu. A
page that hangs in no menu falls back to its own path, naming each step from the route
registered for it. Every trail starts at the front page, and the page itself is the last
crumb, which the theme draws as the current step rather than as a link.

## Administering menus

`/admin/structure/menu` lists the menus and adds one, behind the `administer menu`
permission. `/admin/structure/menu/manage/<id>` lists the links in a menu, indented by
depth, and adds, edits, deletes, reorders, and turns them off. A link a module declared
is listed there but carries no edit or delete button, since it lives in the module.

Ordering is a weight input per link, which submits with or without JavaScript. With
JavaScript, `drag-order.js` lets the rows be dragged and renumbers the weights to match.
