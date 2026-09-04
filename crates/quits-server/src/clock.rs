//! Wall-clock helpers.
//!
//! These feed epoch values that clients compare against their own clocks, so they must come from
//! `SystemTime` rather than a monotonic source.

use std::time::{Duration, SystemTime, UNIX_EPOCH};

pub fn now_ms() -> i64 {
    since_epoch().as_millis() as i64
}

pub fn now_secs() -> u64 {
    since_epoch().as_secs()
}

fn since_epoch() -> Duration {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
}
