-- Work items waiting for a queue worker. An item is claimed by setting a lease;
-- the claimer deletes it once processed, and an item whose lease ran out is
-- claimable again. A failed item waits until available_at to be retried, and
-- once it has failed too often it is quarantined and no longer claimed.
create table queue (
    id bigserial primary key,
    name varchar(255) not null,
    data jsonb not null,
    created_at timestamptz not null,
    available_at timestamptz not null,
    lease_expires_at timestamptz,
    attempts integer not null default 0,
    last_error text,
    quarantined_at timestamptz
);
create index queue_claimable on queue (name, id) where quarantined_at is null;
