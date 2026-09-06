# Tokens and mail

## Tokens

Text that reaches an editor or a recipient can carry `[type:name]` tokens, replaced
when the text is rendered. `TokenReplacer` resolves each token through the
`TokenProvider` registered for its type. An unknown token is left in place, or
dropped when the context asks for unknown tokens to be cleared, and values are
HTML-escaped in sanitize mode.

Core providers:

| Type | Resolves from | Example |
| --- | --- | --- |
| `site` | the `system.site` config object | `[site:name]` |
| `current-user` | the authenticated user of the request | `[current-user:name]` |
| `date` | the current time in a named date format | `[date:short]` |
| `node`, `user`, `term`, `file` | the object the caller put in the token context | `[node:author:name]` |

The four object providers extend `ContextObjectTokenProvider`: the caller places the
subject in the context under the provider's type, and a colon-separated name walks the
object graph through record accessors, JavaBean getters, or map keys.

A module adds a type by registering a `TokenProvider` bean. Providers arrive in bean
order, so a module can supersede a core provider by ordering ahead of it. Every
provider lists the tokens it offers, and `/admin/help/tokens` shows them all.

## Mail

`MailManager.send(MailRequest)` renders and delivers one mail:

1. The subject and body are looked up in the message source for the request's locale,
   falling back to the text itself when there is no translation.
2. Both are run through token replacement, escaping values when the format needs it.
3. The `MailFormatter` named by the request renders the body. Core ships `plain` and
   `html`; a module adds a format by registering another `MailFormatter` bean.
4. The message is delivered by the `MailBackend`.

Mail is sent from the site mail address in `system.site`, with the reply-to address
from `system.mail` when one is set.

`CollectingMailBackend` keeps messages in memory so development and tests can inspect
them. `SmtpMailBackend` sends over SMTP through Spring's `JavaMailSender` and is the
primary backend in the `prod` profile, configured with `SPRINGDROP_MAIL_HOST` and
`SPRINGDROP_MAIL_PORT`. An unreachable mail server does not affect the health
endpoint.
