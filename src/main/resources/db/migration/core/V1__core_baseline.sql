-- Core baseline schema. The primitive persistent stores the kernel relies on:
-- the configuration object store and the key/value state stores. Their Java
-- services (ConfigFactory, StateService) are built over these tables.

create table config (
    name varchar(255) primary key,
    data jsonb not null
);

create table key_value (
    collection varchar(128) not null,
    key varchar(255) not null,
    value jsonb not null,
    primary key (collection, key)
);

create table key_value_expire (
    collection varchar(128) not null,
    key varchar(255) not null,
    value jsonb not null,
    expires_at timestamptz not null,
    primary key (collection, key)
);

create index key_value_expire_expires_at on key_value_expire (expires_at);
