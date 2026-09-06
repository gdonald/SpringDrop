# Hardening

The checks a request passes before it reaches a controller, and the headers it carries
on the way back.

## Trusted hosts

`springdrop.security.trusted-host.patterns` lists the names this site answers to, as
regular expressions matched against the name the request was addressed to, without
regard to case:

```yaml
springdrop:
  security:
    trusted-host:
      patterns:
        - ^springdrop\.example$
        - ^www\.springdrop\.example$
```

An empty list answers to any name, which suits local development. With names listed,
`TrustedHostFilter` answers 400 to anything else before routing, so a request carrying
someone else's Host header cannot make the site write that name into a password reset
link.

## Redirect destinations

A link may nominate where to go next, and that value comes from whoever wrote the link.
`RedirectSafety` accepts a path on this site, and an absolute URL only when its host is
listed in `springdrop.security.redirect.allowed-hosts`. Everything else falls back to
the destination the caller nominated, including `//elsewhere.example/landing`, which
looks like a path and is read by browsers as a site of its own.

The sign-in form carries its `destination` through, so signing in from a page you could
not reach returns you to it, while a destination pointing off-site lands you on the
front page instead.

## Response headers

`SecurityHeadersFilter` writes `X-Frame-Options`, `X-Content-Type-Options`,
`Referrer-Policy`, and `Content-Security-Policy` on every response, all four
configurable under `springdrop.security.headers`. A blank value leaves that header off.

The policy is a template: `{nonce}` in it is replaced with a nonce minted for that one
response and put on the request as `cspNonce`. A template's inline script carries it:

```html
<script type="module" th:attr="nonce=${cspNonce}">
```

A script without the nonce is refused by the browser, which is what makes an injected
`<script>` inert.

## Banned addresses

`/admin/config/people/ban`, open to `ban ip addresses`, lists the addresses the site
refuses, adds one, and lets one back in. `IpBanFilter` answers 403 to a banned address
ahead of routing, so a banned visitor reaches no controller and gets no session. The
list is the `system.banned_ips` config object, so it exports and imports with the rest
of the site's configuration.

## Flood control

Counting how often something has been tried, and refusing past a threshold, is covered
in [Accounts and batches](users-and-batches.md).
