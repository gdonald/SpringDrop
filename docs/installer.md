# Installer

A site whose database has not been installed sends every request to `/install`, except the
installer itself, the batch it runs, the scripts and styles pages load, and the error and
actuator endpoints. Once the site is installed, `/install` sends the person to the home page.
`InstallState` keeps the time the installer finished in the state store, and
`springdrop.install.assume-installed` makes a site count as installed whatever its database
says, as the test suite does.

## Steps

| Step | Path | Does |
| --- | --- | --- |
| Choose language | `/install` | the language the site is built in, from the ISO 639 languages, English by default |
| Verify requirements | `/install/requirements` | checks PostgreSQL 13 or later, every core migration applied, and that the public and private file directories can be created and written; the next step waits until every one is met |
| Choose profile | `/install/profile` | one of the install profiles |
| Configure site | `/install/site` | the site's name and email address, and the administrator's username, email address, and password of at least 8 characters |

A step reached before the ones it needs sends the person back to the first of those. The
choices are kept in the session from step to step.

The last step starts the install batch, watched at `/batch/<id>`, which ends at the sign-in
page:

1. An operation enables each of the profile's modules and the modules they depend on, in
   dependency order. Core's migrations ran as the site started, before the installer.
2. An operation imports each of the profile's config files, each after the config it
   depends on, checked against its schema and dependencies like a
   [single import](config-management.md#single-items). One that is refused fails its
   operation with the reasons.
3. An operation runs the profile's `install()` step.
4. A last operation adds the chosen language when it is not English and makes it the
   default, saves the site's name and address, gives the first account the administrator's
   username, address, and password, and marks the site installed.

## Profiles

An `InstallProfile` bean is a starting point the installer offers: an id, a label, a
description, a weight that orders the list, the modules it enables, config files under
`classpath:springdrop/profiles/<id>/config/`, and an `install()` step for what plain config
cannot set up, run after the config is imported. Each file is named `<name>.yml` and holds
the object's value under `data`, with any dependencies its name does not imply under
`dependencies.config`.

| Profile | Modules | Config |
| --- | --- | --- |
| `standard` | none beyond core | Article and Basic page, Tags, comments, menus, blocks, and roles, listed in [the standard profile](standard-profile.md) |
| `minimal` | none beyond core | none |
| `demo` | none beyond core | the standard profile's config, a notice that the site is a demo and not for production, an Image media type, three sample recipes as promoted articles with pictures, tags, and an image media item each, an About page, and a main menu link to it |
