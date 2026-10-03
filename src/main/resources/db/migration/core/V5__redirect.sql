-- Paths that send the browser elsewhere: an old address of a page, or one
-- an administrator moved. A source has one redirect.
create table redirect (
    id bigserial primary key,
    source varchar(255) not null,
    destination varchar(2048) not null,
    status integer not null
);
create unique index redirect_source on redirect (source);
