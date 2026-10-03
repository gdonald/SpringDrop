# Render pipeline

A page is built as a tree of renderables and turned into markup in one pass, carrying
with it what it depends on.

## Renderables

A `Renderable` names the Thymeleaf fragment that draws it and carries the data that
fragment reads, the attributes to put on its element, the children nested inside it, its
cacheability, and whatever it needs the page to attach:

```java
Renderable card = Renderable.of("container")
        .attribute("class", "card")
        .cacheTag("node:7")
        .cacheContext("user.permissions")
        .styleSheet("/css/card.css")
        .child(Renderable.of("text").with("value", node.label()));
```

`Renderable.of(type)` draws with a fragment of `render/elements.html`, which holds the
handful core needs: `container`, `text`, `markup`, `link`, `list`, and `listItem`. A
module draws with its own template through `Renderable.of(template, type)`, and
`Renderable.of(template, Renderable.WHOLE_TEMPLATE)` draws with a whole template rather
than one fragment of it, which is how theme templates are written.

A fragment reads the node's data by name, the rendered markup of its children as
`children`, and its attributes as a map:

```html
<div th:fragment="text" th:id="${attributes['id']}" th:classappend="${attributes['class']}"
     th:text="${value}">text</div>
```

Thymeleaf sets attributes it can name, so a fragment reads the attributes it cares
about rather than spreading the whole map onto its element.

### Slots

A child put in a named slot is drawn into its parent's `slots` map under that name
instead of into `children`, so a template places it on its own:

```java
Renderable.of(template, "columns")
        .child(Renderable.of("text").with("value", "Agenda").inSlot("side"))
        .child(Renderable.of("text").with("value", "Minutes"));
```

```html
<div th:fragment="columns">
  <aside th:utext="${slots['side']}"></aside>
  <section th:utext="${children}"></section>
</div>
```

Several children in one slot are drawn one after another. A slotted child bubbles its
cacheability and attachments like any other child.

## Bubbling

`RenderService.render` returns a `RenderedPage`: the markup, the cacheability merged
from every node in the tree, and everything those nodes asked the page to carry.

Merging is what makes bubbling work. Contexts and tags are unions, so a page carries
every tag any part of it depends on and invalidating one tag drops the page. Max age is
the shorter of the two, so a page is only as reusable as its least reusable part, and
one uncacheable part makes the whole page uncacheable. Attachments merge the same way,
and each style sheet or script is carried once however many nodes asked for it.

## Placeholders

A part that varies per visitor holds back a page that would otherwise be reusable.
`Renderable.lazy` puts a placeholder there instead:

```java
Renderable.of("container")
        .cacheTag("config:system.site")
        .child(Renderable.lazy(() -> Renderable.of("text")
                .with("value", "Signed in as " + account.name())
                .cacheContext("user")
                .maxAge(Duration.ZERO)));
```

The shell renders with a marker in place of the lazy part, and the builder runs once the
shell is done. What it builds is dropped into the marker's place and contributes its own
cacheability from there, so the shell's metadata describes the shell and the page's
describes the whole. A built part may hold placeholders of its own, which are filled in
turn. This is the piece a dynamic page cache and a streamed first render are built on.

## The render cache

`Renderable.cacheKeys(...)` keeps a node's markup in the render cache:

```java
Renderable.of("markup")
        .with("value", teaser)
        .cacheTag(CacheTags.entity("node", 7))
        .cacheContext(CacheContexts.USER_ROLES)
        .cacheKeys("teaser", "7");
```

`RenderService` looks the node up before drawing it. A kept entry is used as it is, and
brings its cacheability and attachments to the page. Otherwise the node is drawn and
kept, unless its merged cacheability may not be cached or its drawing holds a
placeholder, since a placeholder is filled in for one drawing only.

`RenderCache` keeps entries in the Spring cache `render`, from the `CacheManager` the
site defines, held in memory. An entry is kept under its keys and the values of its
cache contexts. A node's drawing can vary by contexts that only parts inside it carry,
so when the merged contexts are more than the node's own, the entry under the node's own
contexts holds a redirect naming all of them, and the markup is kept under those. An
entry is used until one of its tags is invalidated or its max age passes.

### Cache tags

`CacheTags` names the tags the site invalidates on its own:

| Tag | Invalidated when |
| --- | --- |
| `<entity type>:<id>`, such as `node:42` | the entity is saved or deleted |
| `<entity type>_list`, such as `node_list` | any entity of the type is saved or deleted, or the search index of the type changes |
| `config:<name>`, such as `config:system.site` | the config object is saved or deleted |

`CacheTagInvalidator.invalidate(tags)` invalidates tags. It keeps a count of
invalidations per tag, and an entry keeps the checksum of its tags' counts from when it
was stored, so an entry whose tag was invalidated since no longer matches and is
dropped when next read. Unrelated entries are left as they are. Each invalidation also
publishes a `CacheTagsInvalidatedEvent`, which caches kept outside the render cache,
such as the views cache, listen to. The counts are kept in memory, so another instance
of the site keeps its own.

### Cache contexts

`CacheContexts` reads each context's value for the current request:

| Context | Varies by |
| --- | --- |
| `user` | the account's name, empty for someone not signed in |
| `user.roles` | the roles held |
| `user.permissions` | the authorities held |
| `languages` | the request's language |
| `url` | the path and query string |
| `url.path`, `route` | the path |
| `url.query_args` | every query argument |
| `url.query_args:<name>` | one query argument |

Outside a request the request contexts read as empty. A context the site does not have
is refused.

## Named placeholders

`Renderable.placeholder(builderId, arguments)` is a placeholder the `PlaceholderBuilder`
bean with that id fills, given the arguments. Unlike `Renderable.lazy`, whose builder is
code from one request, a named placeholder can be built again for another request, which
is what lets a page kept with it be reused. A placeholder naming a builder the site does
not have is refused when the page is drawn.

`RenderService.renderShell(root)` draws a tree with a marker for each placeholder and
gives a `Shell`: the markup, what the drawing carries without the placeholders, and
their builders. `RenderService.fill(shell)` builds the placeholders into it. `render` is
the two in turn.

## Page caches

`PageCacheFilter` answers reads of pages from the `PageCache`, which keeps them in the
Spring cache `page`. Every answer it considers carries the header `X-SpringDrop-Cache`,
`HIT` or `MISS`.

A page is kept only when its controller opts in with `PageChrome.withPageCache()`, after
checking that everything the page shows carries the cache tags and contexts it depends
on. The front page and its listing at `/node` opt in. The page renderer adds what the
chrome depends on: `config:system.site`, the main menu's tags, and the
`user.permissions` context. A page showing status messages may not be cached.

| Reader | Kept | Varies by |
| --- | --- | --- |
| not signed in, with no session | the finished page | the address and the page's contexts |
| signed in | the page's shell, its named placeholders built for each request | the address, the account, and the shell's contexts |

The address is the path the reader asked for, before an alias or a view page was
answered in its place, with the query arguments in order of name. A page is kept only
when it answered 200 to a `GET` or `HEAD`. It is not kept when it holds a form's CSRF
token, which belongs to one session, when a page for someone not signed in started a
session, or when a shell holds a placeholder that is not named. The nonce the page's
inline scripts carry is replaced with the one minted for each request it answers.

A kept page is used until one of its tags is invalidated, or for at most
`springdrop.cache.page-max-age`, ten minutes unless that says otherwise. The limit
covers what a page shows without tags of its own, such as entities referenced from a
teaser, its tabs, and its breadcrumb.

## Streaming placeholders

For someone signed in whose browser runs scripts, a page with placeholders is streamed
in parts: `PageRenderer.finish` leaves the placeholders out, marking each with a
`data-big-pipe-placeholder-id` span, and `BigPipeFilter` sends the page up to the end of
its body and flushes it. Each placeholder is then built and sent as a JSON data script,

```html
<script type="application/json" data-big-pipe-replacement-for="placeholder-1">{"html":"..."}</script>
```

flushed in turn, and the rest of the page follows. `/js/big-pipe.js`, loaded by an
inline module in the head, puts each placeholder's markup in its marker's place as its
script arrives. A placeholder's style sheets are sent with its markup. The filter runs
inside the theme filter, so placeholders are drawn in the request's theme, and outside
the page caches, so a page answered from the dynamic page cache is streamed as well.

The shell's head holds a `noscript` refresh to
`/big-pipe/no-js?destination=<the page>`, which sets the `springdrop-nojs` cookie and
sends the reader back, or to the front page for a destination off the site. With the cookie, pages are drawn whole, their placeholders built
in place. Someone not signed in always gets the page whole.

## Script assets

The site's script modules live in `static/js`, one file each, served at `/js/<module>.js`
in development. A template loads a module through the `assets` bean:

```html
<script type="module" th:attr="nonce=${cspNonce}" th:inline="javascript">
  import { initFormStates } from [[${@assets.url('/js/form-states.js')}]];
  initFormStates();
</script>
```

The build bundles them for production. `assetEntry` writes an entry re-exporting every
module's `init` functions, `bundleAssets` bundles and minifies it with esbuild from
`node_modules` (`./test.sh` installs it when missing), and `fingerprintAssets` names the
bundle with the first 16 hex digits of its SHA-256 and records it in
`springdrop/assets.properties`. The site imports that file as config.

With `springdrop.assets.aggregate` on, as the `prod` profile sets it, `assets.url` gives
the bundle's address, `/assets/springdrop.<hash>.js`, for every module. The bundle is
served as public and immutable with a `Cache-Control` max age of a year, since a change to any
module gives it a new name. Aggregation on with no bundle named stops the site at start.
With it off, each module is loaded from its own address.

## The theme layer

A renderable names one fragment. The theme layer chooses which, so a site changes what
something looks like by adding a template rather than by editing the code that renders
it.

`TemplateSuggestions` builds the names a piece of output may be drawn by, most specific
first. An article node id 7 rendered in the teaser view mode suggests:

```
node--7--teaser, node--7, node--article--teaser, node--article, node--teaser, node
```

`ThemeService` takes the suggestions and the variables and hands back a renderable:

```java
Renderable article = themes.entity("content", "node", "article", "teaser", "7",
        Map.of("title", node.label(), "body", body));
```

A themed template is one file holding one piece of output, with no fragment to name, so
a theme overrides one by putting a file of the same name under its own root and needs to
know nothing about the file it replaces. Themed templates render outside a web request,
so a URL reaches them as data rather than through `@{...}`.

### Themes and resolution

A `Theme` is a name, an optional parent, and the template root its files live under.
`Theme.named("vista")` roots at `themes/vista`, and `Theme.extending("vista-child",
"vista")` roots at `themes/vista-child` and falls back to `vista`. `ThemeRegistry` holds
the registered themes and which one is active.

Resolution runs suggestions outermost and the theme chain innermost. The most specific
template wins wherever it lives, so a core `node--article` beats a theme's `node`.
Between two templates of the same specificity the active theme beats its parent, which
beats the core codebase. Nothing in core is edited to override it.

### Preprocessing

Once the template is chosen and before its variables reach the renderer,
`ThemePreprocessEvent` carries the hook, the template that won, every suggestion, and the
mutable variable map. A listener adds, replaces, or drops variables in place:

```java
@EventListener
void addAByline(ThemePreprocessEvent event) {
    if (event.hook().equals("node")) {
        event.subject().put("byline", byline(event.subject().get("uid")));
    }
}
```

## The base theme

`front-end` is the theme registered as the default, rooted at
`templates/themes/front-end`. It holds the page layout and the partials every page
draws.

`PageChrome` carries what goes around the content: the site name and slogan, the page
title, the primary navigation, breadcrumbs, local task tabs, local actions, status
messages, and a pager. Each part is optional, and a part that is empty draws nothing.
`PageRenderer` turns a chrome and a content renderable into a page:

```java
Renderable content = themes.build("content", TemplateSuggestions.of("front-page"),
        Map.of("welcome", WELCOME));

PageChrome chrome = PageChrome.of(site.name(), site.name())
        .withSlogan(site.slogan())
        .withPrimaryNavigation(List.of(new Link("Home", "/")))
        .withLocalActions(List.of(new Link("Edit", "/admin")));

return pages.render(chrome, content).html();
```

The content is a child of the layout, so its cache tags and attachments bubble through
the layout to the page. `PageRenderer` writes the attachments into the page: style sheets
and head tags at the end of the head, and scripts at the end of the body as modules.

`PageChrome.withRegion(region, renderable)` puts something in a region of the layout.
Each region is a child of the layout in the slot named after it, and the layout draws
`slots['sidebar']` where the sidebar goes. The block system fills the regions from the
blocks placed in them, as `docs/block-system.md` describes.

### Layout and partials

`layout/page` is the document: a Bootstrap navbar that collapses behind a toggler below
the large breakpoint, a `container` holding a `row` whose content column is
`col-12 col-lg-8`, and a footer. It draws the `header`, `primary_menu`, `breadcrumb`,
`highlighted`, `help`, `content`, `sidebar`, and `footer` regions, and adds a
`col-12 col-lg-4` sidebar column only when the sidebar holds something.

Partials live in `partials/` and draw one part each: `menu`, `breadcrumb`, `tabs`,
`local-actions`, `messages`, and `pager`. The `menu` partial draws `primaryNavigation`,
whose links carry their own children, as a navbar with a dropdown per branch.

The layout draws each partial by resolved path rather than by name, which
`PageRenderer` passes in as `partials`:

```html
<div th:replace="${partials['pager']}"></div>
```

A theme overrides the pager by adding `partials/pager.html` under its own root. Nothing
about the layout changes.

### Control conventions

Local actions render as `btn btn-primary btn-sm`, never as outline buttons, so an Edit
action is a button wherever it appears. `BootstrapAssertions` checks both rules over
rendered markup, and the base theme tests run them against a page carrying every part of
the chrome.

## The admin theme

`admin` extends `front-end`, so it overrides the layout and inherits every partial. Its
layout puts the toolbar across the top as a dark navbar, gives the content the full
width in a `container-fluid`, and runs tables tighter than the front end does.

Which theme a request renders under is negotiated per request. `ThemeResolver` picks
`admin` for a route registered as an admin route or any path under `/admin`, and
`front-end` otherwise. `ThemeNegotiationFilter` activates that theme for the request and
drops it when the request is done.

The active theme is held per thread rather than shared, so two requests under different
themes do not read each other's. `ThemeRegistry.active()` falls back to the site default
from `springdrop.theme.default` whenever no theme was activated for the current thread.
