-- The last update routine applied for each module, and core as "system", so an
-- update runs once and a later run skips it.
create table module_update (
    module varchar(128) primary key,
    version integer not null,
    applied_at timestamptz not null
);
