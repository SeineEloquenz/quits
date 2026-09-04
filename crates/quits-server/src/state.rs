//! Shared application state, cloned into every request handler.

use std::sync::Arc;

use sqlx::SqlitePool;

use crate::config::Config;
use crate::telemetry::Metrics;

#[derive(Clone)]
pub struct AppState {
    pub db: SqlitePool,
    pub config: Arc<Config>,
    pub metrics: Metrics,
}

impl AppState {
    pub fn new(db: SqlitePool, config: Config) -> Self {
        let metrics = Metrics::new(&config);
        Self {
            db,
            config: Arc::new(config),
            metrics,
        }
    }
}
