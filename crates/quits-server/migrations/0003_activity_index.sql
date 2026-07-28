-- Supports the stale-group reaper's per-group MAX(updated_at) activity scan.
CREATE INDEX IF NOT EXISTS idx_records_group_updated ON records (group_id, updated_at);
