# API authentication

The web service endpoints, REST under `/rest` and JSON:API under `/jsonapi`, accept two
ways of signing in: the session cookie a browser holds after signing in, and a personal
access token sent as a bearer token. Either way the request carries the account's
permissions, so the same permission and access checks apply.

## Bearer tokens

`/user/tokens` lists the signed-in account's tokens, creates one under a label of at most
128 characters, and revokes one. The page needs `use api tokens`. A new token is shown once,
on the page that creates it. Only its SHA-256 hash is stored, in the `api_token` table, and
deleting an account revokes its tokens.

A request sends the token in the `Authorization` header:

```
Authorization: Bearer sd_3q2...
```

`BearerTokenFilter` signs in a request to `/rest` or `/jsonapi` that sends a bearer token,
as the token's account, for that request alone. No session is made. A bearer header on any
other path is ignored. Each use records when the token was last used.

| Status | When |
| --- | --- |
| 401 | the token matches no token, or its account is blocked, with `WWW-Authenticate: Bearer realm="SpringDrop", error="invalid_token"` |
| 429 | the address sent 50 tokens that were not valid within the last hour |

## Session cookies and CSRF

A write signed in with a session cookie needs the session's CSRF token, so a page on another
site cannot send a write in the visitor's name. `GET /session/token` answers the token as
plain text, and the write sends it in the `X-CSRF-TOKEN` header. A write without it is
refused with a 403. A request signed in with a bearer token needs no CSRF token, since no
browser sends that header on its own.

## Refusals

A request refused for a missing permission or entity access is answered with a 403 when
the person is signed in and with a 401 carrying `WWW-Authenticate: Bearer realm="SpringDrop"`
when they are not. A refusal under `/jsonapi` is a JSON:API errors document, and one under
`/rest` is a JSON object with a `message`.
