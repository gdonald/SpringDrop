-- Which things use each managed file, and how many times. A file nothing uses
-- goes back to being temporary, and cron removes temporary files once they are
-- old enough.
create table file_usage (
    fid bigint not null,
    module varchar(64) not null,
    type varchar(64) not null,
    id varchar(64) not null,
    count integer not null,
    primary key (fid, module, type, id)
);
create index file_usage_type_id on file_usage (type, id);
