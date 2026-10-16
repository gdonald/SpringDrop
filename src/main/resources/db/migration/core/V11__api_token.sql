-- Personal access tokens an account sends as "Authorization: Bearer <token>" to
-- call the REST and JSON:API endpoints. Only the SHA-256 hash of each token is
-- kept, so a copy of the table holds no working token.
create table api_token (
    id bigserial primary key,
    account_id bigint not null,
    label varchar(128) not null,
    token_hash char(64) not null,
    created timestamptz not null,
    last_used timestamptz
);

create unique index api_token_hash on api_token (token_hash);

create index api_token_account on api_token (account_id);
