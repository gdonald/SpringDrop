-- Named locks held across requests and processes, such as the one cron runs
-- under. A row is a held lock: its owner releases it, and a lock whose holder
-- died lapses at expires_at so the next caller can take it.
create table semaphore (
    name varchar(255) primary key,
    owner varchar(64) not null,
    expires_at timestamptz not null
);
