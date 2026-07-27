-- E2EE flag day: a group is found by an opaque lookup id the client derives from the group secret,
-- which the relay never sees. Pre-E2EE plaintext records are unreadable by new clients, so drop them.
DELETE FROM records;
DELETE FROM groups;
ALTER TABLE groups RENAME COLUMN code TO lookup_id;
