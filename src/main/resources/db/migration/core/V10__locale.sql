-- Interface strings and their translations. A source string is recorded the
-- first time it is asked to be translated, so the translation page lists it.
-- The context tells apart two uses of the same English words. A plural source
-- holds its singular and plural forms joined by the character U+0003, and so
-- does its translation.
create table locale_source (
    id bigserial primary key,
    source text not null,
    context varchar(255) not null default '',
    unique (source, context)
);

create table locale_target (
    source_id bigint not null references locale_source (id) on delete cascade,
    langcode varchar(12) not null,
    translation text not null,
    primary key (source_id, langcode)
);
