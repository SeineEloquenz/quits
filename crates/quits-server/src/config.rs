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
}

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
