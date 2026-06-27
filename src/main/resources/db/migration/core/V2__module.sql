-- Tracks which modules are installed and enabled. A row exists once a module is
-- installed; the enabled flag reflects whether it is currently active.
create table module (
    name varchar(128) primary key,
    enabled boolean not null default false
);
