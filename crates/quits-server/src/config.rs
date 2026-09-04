//! Runtime configuration, sourced from the environment.

use std::str::FromStr;
use std::time::Duration;

use sqlx::sqlite::{SqliteConnectOptions, SqliteJournalMode};
use uuid::Uuid;

/// Relay configuration. Secrets default to a freshly generated ephemeral value
#[derive(Clone)]
pub struct Config {
    pub addr: String,
    pub database_url: String,
    pub db_max_connections: u32,

    /// HS256 key for group access tokens.
    pub jwt_secret: Vec<u8>,
    /// Lifetime of an issued group token.
    pub token_ttl_secs: u64,

    /// Optional instance lock: when set, group creation requires this secret in `X-Quits-Instance`.
    pub instance_secret: Option<String>,

    // --- Abuse safeguards for a public (unlocked) instance. ---
    /// Trust `trusted_ip_header` for the real client IP. Only enable behind a reverse proxy
    /// that sets it; otherwise a client could spoof it and dodge per-IP limits.
    pub behind_proxy: bool,
    /// Header carrying the real peer IP when `behind_proxy` is set (e.g. `X-Real-IP`).
    pub trusted_ip_header: String,
    /// Global per-IP request limiter: burst bucket + replenish interval. `0` burst disables it.
    pub rate_burst: u32,
    pub rate_replenish_ms: u64,
    /// Stricter per-IP limiter on group creation only. `0` burst disables it.
    pub create_burst: u32,
    pub create_replenish_secs: u64,
    /// Hard ceiling on total groups; creation past it returns 503. `0` disables.
    pub max_groups: u64,
    /// Max request body size in bytes (bounds push batch size).
    pub max_body_bytes: usize,
    /// Max size of a single record payload after base64 decode. `0` disables.
    pub max_record_bytes: usize,
    /// Max records retained per group; new inserts past it are rejected. `0` disables.
    pub max_records_per_group: u64,
    /// Reap groups with no records older than this many seconds. `0` disables.
    pub empty_group_ttl_secs: u64,
    /// Reap groups whose newest record is older than this many seconds. `0` disables.
    pub inactive_group_ttl_secs: u64,
    /// How often the background reaper runs.
    pub reap_interval_secs: u64,
    /// How often the storage gauges are refreshed.
    pub stats_interval_secs: u64,
    /// Prometheus metrics endpoint address. `None` disables the endpoint entirely.
    pub metrics_addr: Option<String>,

    /// Android release signing SHA-256 fingerprint(s) advertised in `/.well-known/assetlinks.json`
    pub android_cert_sha256: Vec<String>,
    /// Apple `TeamID.bundleId` advertised in the AASA. Placeholder until iOS ships.
    pub ios_app_id: String,
}

/// Official Quits release signing fingerprint; self-hosters override via `QUITS_ANDROID_CERT_SHA256`.
const DEFAULT_ANDROID_CERT_SHA256: &str = "23:F2:05:A9:19:B0:8F:F7:29:BA:E4:21:51:22:47:D9:FF:0E:FC:16:8B:21:A0:1F:A4:C7:47:C1:8A:A9:8B:47";
/// Placeholder Apple app id (no real Team ID yet); Universal Links won't verify until this is real.
const DEFAULT_IOS_APP_ID: &str = "TEAMID0000.nz.eloque.quits";

impl Config {
    /// Loads configuration from the environment, applying defaults.
    pub fn from_env() -> Self {
        Self {
            addr: env_or("QUITS_ADDR", "0.0.0.0:8080"),
            database_url: env_or("DATABASE_URL", "sqlite:quits.db"),
            db_max_connections: env_parse("QUITS_DB_MAX_CONNECTIONS", 5),
            jwt_secret: env_secret("QUITS_JWT_SECRET"),
            token_ttl_secs: env_parse("QUITS_TOKEN_TTL_SECS", 60 * 60 * 24 * 3650),
            instance_secret: std::env::var("QUITS_INSTANCE_SECRET")
                .ok()
                .filter(|s| !s.is_empty()),
            behind_proxy: env_bool("QUITS_BEHIND_PROXY", false),
            trusted_ip_header: env_or("QUITS_TRUSTED_IP_HEADER", "X-Real-IP"),
            rate_burst: env_parse("QUITS_RATE_BURST", 60),
            rate_replenish_ms: env_parse("QUITS_RATE_REPLENISH_MS", 500),
            create_burst: env_parse("QUITS_CREATE_BURST", 5),
            create_replenish_secs: env_parse("QUITS_CREATE_REPLENISH_SECS", 600),
            max_groups: env_parse("QUITS_MAX_GROUPS", 0),
            max_body_bytes: env_parse("QUITS_MAX_BODY_BYTES", 1024 * 1024),
            max_record_bytes: env_parse("QUITS_MAX_RECORD_BYTES", 8 * 1024),
            max_records_per_group: env_parse("QUITS_MAX_RECORDS_PER_GROUP", 5_000),
            empty_group_ttl_secs: env_parse("QUITS_EMPTY_GROUP_TTL_SECS", 60 * 60 * 48),
            inactive_group_ttl_secs: env_parse("QUITS_INACTIVE_GROUP_TTL_SECS", 60 * 60 * 24 * 180),
            reap_interval_secs: env_parse("QUITS_REAP_INTERVAL_SECS", 3600),
            stats_interval_secs: env_parse("QUITS_STATS_INTERVAL_SECS", 300),
            metrics_addr: std::env::var("QUITS_METRICS_ADDR")
                .ok()
                .filter(|s| !s.is_empty()),
            android_cert_sha256: env_list("QUITS_ANDROID_CERT_SHA256", DEFAULT_ANDROID_CERT_SHA256),
            ios_app_id: env_or("QUITS_IOS_APP_ID", DEFAULT_IOS_APP_ID),
        }
    }

    /// A permissive configuration for tests
    pub fn for_test(database_url: impl Into<String>) -> Self {
        Self {
            addr: "127.0.0.1:0".into(),
            database_url: database_url.into(),
            db_max_connections: 5,
            jwt_secret: b"test-jwt-secret".to_vec(),
            token_ttl_secs: 3600,
            instance_secret: None,
            // Safeguards off by default in tests; individual tests opt in explicitly.
            behind_proxy: false,
            trusted_ip_header: "X-Real-IP".to_string(),
            rate_burst: 0,
            rate_replenish_ms: 500,
            create_burst: 0,
            create_replenish_secs: 600,
            max_groups: 0,
            max_body_bytes: 1024 * 1024,
            max_record_bytes: 0,
            max_records_per_group: 0,
            empty_group_ttl_secs: 0,
            inactive_group_ttl_secs: 0,
            reap_interval_secs: 3600,
            stats_interval_secs: 300,
            metrics_addr: None,
            android_cert_sha256: vec![DEFAULT_ANDROID_CERT_SHA256.to_string()],
            ios_app_id: DEFAULT_IOS_APP_ID.to_string(),
        }
    }

    /// SQLite connection options derived from `database_url`: create the file if missing and use
    /// WAL for read/write concurrency.
    pub fn sqlite_connect_options(&self) -> Result<SqliteConnectOptions, sqlx::Error> {
        Ok(SqliteConnectOptions::from_str(&self.database_url)?
            .create_if_missing(true)
            .journal_mode(SqliteJournalMode::Wal)
            .busy_timeout(Duration::from_secs(5)))
    }
}

fn env_or(key: &str, default: &str) -> String {
    std::env::var(key).unwrap_or_else(|_| default.to_string())
}

/// Comma-separated list env var, trimmed with empties dropped; falls back to `default` when unset.
fn env_list(key: &str, default: &str) -> Vec<String> {
    let raw = std::env::var(key)
        .ok()
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| default.to_string());
    raw.split(',')
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .map(str::to_string)
        .collect()
}

/// Parses a boolean env var; accepts `1`/`true`/`yes`/`on` (case-insensitive) as true.
fn env_bool(key: &str, default: bool) -> bool {
    match std::env::var(key) {
        Ok(v) => matches!(
            v.trim().to_ascii_lowercase().as_str(),
            "1" | "true" | "yes" | "on"
        ),
        Err(_) => default,
    }
}

fn env_parse<T: FromStr>(key: &str, default: T) -> T {
    std::env::var(key)
        .ok()
        .and_then(|v| v.parse().ok())
        .unwrap_or(default)
}

fn env_secret(key: &str) -> Vec<u8> {
    match std::env::var(key) {
        Ok(s) if !s.is_empty() => s.into_bytes(),
        _ => {
            tracing::warn!(
                "{key} not set; generating an ephemeral secret (tokens/challenges won't survive a restart)"
            );
            random_secret()
        }
    }
}

/// 32 random bytes, sourced from the CSPRNG behind `uuid::Uuid::new_v4`.
fn random_secret() -> Vec<u8> {
    let mut v = Vec::with_capacity(32);
    v.extend_from_slice(Uuid::new_v4().as_bytes());
    v.extend_from_slice(Uuid::new_v4().as_bytes());
    v
}
