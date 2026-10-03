-- The words of each indexed entity, for keyword search. The document column
-- is kept from the title and body by Postgres, the title weighted above the
-- body, and a GIN index matches tsquery searches against it.
create table search_index (
    entity_type varchar(64) not null,
    entity_id bigint not null,
    langcode varchar(12) not null,
    title text not null,
    body text not null,
    document tsvector generated always as (
        setweight(to_tsvector('english', title), 'A') || setweight(to_tsvector('english', body), 'B')
    ) stored,
    primary key (entity_type, entity_id, langcode)
);
create index search_index_document on search_index using gin (document);

-- Entities saved since they were last indexed, waiting for cron.
create table search_pending (
    entity_type varchar(64) not null,
    entity_id bigint not null,
    primary key (entity_type, entity_id)
);
