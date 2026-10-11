# Status report

`/admin/reports/status` shows how the site stands: a count of errors and warnings, then a
line per check, errors first, then warnings, then the rest, each group by title. A line
gives what is checked, its value, what it means, and a button to where it is fixed when
there is one. The page needs `access site reports`.

## Lines

A `StatusCheck` bean gives `Requirement`s: a title, a value, a description, a
`RequirementSeverity` (`INFO`, `OK`, `WARNING`, or `ERROR`), and an optional link.
`StatusReport.run()` gathers and orders them. A module adds lines by registering a check.

Core's checks, in `CoreStatusChecks`:

| Line | OK | Warning | Error |
| --- | --- | --- | --- |
| Database | answers `select version()` | | cannot be reached |
| Database updates | no core migration pending | | a core migration in `db/migration/core` not applied |
| Cron maintenance tasks | ran within 2 days | last ran 2 to 14 days ago | never ran, or 14 days or more ago |
| Cache backend | (info) the `CacheManager` in use and its caches | | |
| Public, private, and temporary directories | writable | | cannot be created or written |
| Mail server | `spring.mail.host` is set | not set | |
| Trusted host patterns | `springdrop.security.trusted-host.patterns` is set | not set | |
| Account registration | not open, or open with email verification | open without email verification | |

The cron line links to the cron page, where **Run cron** runs it. Module migrations keep
their own history tables and are not counted under database updates.
