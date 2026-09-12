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
the layout to the page.

### Layout and partials

`layout/page` is the document: a Bootstrap navbar that collapses behind a toggler below
the large breakpoint, a `container` holding a `row` whose content column is
`col-12 col-lg-8`, and a footer. Partials live in `partials/` and draw one part each:
`breadcrumb`, `tabs`, `local-actions`, `messages`, and `pager`.

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
