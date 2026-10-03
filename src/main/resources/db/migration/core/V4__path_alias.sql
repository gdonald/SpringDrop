-- Other paths a page answers at. An alias is unique within a language, and a
-- source has at most one alias per language. A generated alias was made from
-- a pattern rather than typed.
create table path_alias (
    id bigserial primary key,
    source varchar(255) not null,
    alias varchar(255) not null,
    langcode varchar(12) not null,
    generated boolean not null default false
);
create unique index path_alias_alias_langcode on path_alias (alias, langcode);
create unique index path_alias_source_langcode on path_alias (source, langcode);
