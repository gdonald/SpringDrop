-- Every config object keeps a uuid across exports and imports, so an import can
-- tell an object that was renamed from one that was deleted and another created.
alter table config add column uuid uuid not null default gen_random_uuid();

create unique index config_uuid on config (uuid);
