-- Quits sync relay schema.
--
-- The relay is domain-agnostic: `records.payload` is opaque.
CREATE TABLE IF NOT EXISTS groups (
    id TEXT PRIMARY KEY NOT NULL,
    code TEXT NOT NULL UNIQUE,
    created_at INTEGER NOT NULL
);
-- Monotonic change counter shared by all records: gives a global order we can page through,
-- filtered per group. A single-row table bumped inside the push transaction.
CREATE TABLE IF NOT EXISTS change_seq (
    id INTEGER PRIMARY KEY CHECK (id = 0),
    value INTEGER NOT NULL
);
INSERT
    OR IGNORE INTO change_seq (id, value)
VALUES (0, 0);
CREATE TABLE IF NOT EXISTS records (
    group_id TEXT NOT NULL REFERENCES groups (id),
    -- client-generated uuid
    id TEXT NOT NULL,
    -- client epoch millis (last-write-wins clock)
    updated_at INTEGER NOT NULL,
    -- tombstone (0/1)
    deleted INTEGER NOT NULL DEFAULT 0,
    -- last-write-wins tiebreak
    device_id TEXT NOT NULL,
    -- opaque (JSON now, ciphertext later)
    payload BLOB NOT NULL,
    -- assigned from change_seq on every apply
    server_seq INTEGER NOT NULL,
    PRIMARY KEY (group_id, id)
);
CREATE INDEX IF NOT EXISTS idx_records_group_seq ON records (group_id, server_seq);