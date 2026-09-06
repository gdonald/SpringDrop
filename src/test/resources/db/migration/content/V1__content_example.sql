-- Owned by the content test module, which depends on foundation.
create table content_example (
    id integer primary key,
    foundation_id integer not null references foundation_example (id)
);
