//! Background cleanup of stale groups, so an open relay's storage doesn't grow without bound.
//!
//! Two rules, each independently toggleable via TTL config:
//!   - *empty* groups (created but never populated) are removed after a short grace period;
//!   - *inactive* groups (newest record older than a long TTL) are removed along with their records.

use std::sync::Arc;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use sqlx::SqlitePool;

use crate::config::Config;

#[derive(Debug, Default, Clone, Copy)]
pub struct ReapStats {
    pub empty: u64,
    pub inactive: u64,
}

impl ReapStats {
    fn total(&self) -> u64 {
        self.empty + self.inactive
    }
}

/// Runs one cleanup pass. Safe to call from tests directly.
pub async fn reap_once(db: &SqlitePool, config: &Config) -> Result<ReapStats, sqlx::Error> {
    let now = now_ms();
    let mut stats = ReapStats::default();

    // Empty groups: no records and older than the grace period. Non-destructive to real data.
    if config.empty_group_ttl_secs > 0 {
        let threshold = now - (config.empty_group_ttl_secs as i64) * 1000;
        let res = sqlx::query(
            "DELETE FROM groups
             WHERE created_at < ?
               AND NOT EXISTS (SELECT 1 FROM records WHERE records.group_id = groups.id)",
        )
        .bind(threshold)
        .execute(db)
        .await?;
        stats.empty = res.rows_affected();
    }

    // Inactive groups: newest record (or created_at when empty) older than the long TTL.
    if config.inactive_group_ttl_secs > 0 {
        let threshold = now - (config.inactive_group_ttl_secs as i64) * 1000;
        let ids: Vec<(String,)> = sqlx::query_as(
            "SELECT g.id FROM groups g
             LEFT JOIN (
                 SELECT group_id, MAX(updated_at) AS last FROM records GROUP BY group_id
             ) r ON r.group_id = g.id
             WHERE COALESCE(r.last, g.created_at) < ?",
        )
        .bind(threshold)
        .fetch_all(db)
        .await?;

        // Delete records before the group (no ON DELETE CASCADE on the FK).
        let mut tx = db.begin().await?;
        for (id,) in &ids {
            sqlx::query("DELETE FROM records WHERE group_id = ?")
                .bind(id)
                .execute(&mut *tx)
                .await?;
            sqlx::query("DELETE FROM groups WHERE id = ?")
                .bind(id)
                .execute(&mut *tx)
                .await?;
        }
        tx.commit().await?;
        stats.inactive = ids.len() as u64;
    }

    Ok(stats)
}

/// Spawns the periodic reaper. No-op when both TTLs are disabled.
pub fn spawn(db: SqlitePool, config: Arc<Config>) {
    if config.reap_interval_secs == 0
        || (config.empty_group_ttl_secs == 0 && config.inactive_group_ttl_secs == 0)
    {
        return;
    }
    tokio::spawn(async move {
        let mut ticker = tokio::time::interval(Duration::from_secs(config.reap_interval_secs));
        loop {
            ticker.tick().await;
            match reap_once(&db, &config).await {
                Ok(stats) if stats.total() > 0 => {
                    tracing::info!(
                        "reaper removed {} empty and {} inactive groups",
                        stats.empty,
                        stats.inactive
                    );
                }
                Ok(_) => {}
                Err(e) => tracing::error!("reaper pass failed: {e}"),
            }
        }
    });
}

fn now_ms() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as i64
}
