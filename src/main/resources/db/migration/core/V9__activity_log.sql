-- The database backend of the activity log: one row per logged event, with the
-- message template and the variables filled into it kept apart so the viewer
-- can escape the variables. Severity follows RFC 5424, 0 (emergency) to 7 (debug).
create table activity_log (
    id bigserial primary key,
    type varchar(64) not null,
    severity smallint not null,
    message text not null,
    variables jsonb not null,
    uid bigint not null,
    location text not null,
    referer text not null,
    hostname varchar(128) not null,
    created_at timestamptz not null
);
create index activity_log_type on activity_log (type);
create index activity_log_severity on activity_log (severity);
