//! Quits — a domain-agnostic sync relay.
//!
//! The relay stores **opaque records** per group and relays deltas with last-write-wins. It has no
//! domain logic and never interprets payloads (JSON today, ciphertext under e2e later) — all
//! money/split/balance/FX logic lives in the client. See [`routes`] for the protocol.

pub mod auth;
pub mod config;
pub mod error;
mod routes;
pub mod state;

use axum::Router;
use axum::routing::{get, post};
use sqlx::sqlite::SqlitePoolOptions;
use tokio::net::TcpListener;
use tokio::signal;
use tower_http::cors::CorsLayer;
use tower_http::trace::TraceLayer;

use crate::config::Config;
use crate::state::AppState;

/// Opens (creating if necessary) the SQLite pool and runs migrations.
pub async fn build_state(config: Config) -> Result<AppState, sqlx::Error> {
    let pool = SqlitePoolOptions::new()
        .max_connections(config.db_max_connections)
        .connect_with(config.sqlite_connect_options()?)
        .await?;

    sqlx::migrate!("./migrations").run(&pool).await?;

    Ok(AppState::new(pool, config))
}

pub fn router(state: AppState) -> Router {
    Router::new()
        .route("/health", get(routes::health))
        .route("/v1/groups", post(routes::create_group))
        .route("/v1/groups/join", post(routes::join_group))
        .route(
            "/v1/groups/{id}/changes",
            get(routes::get_changes).post(routes::post_changes),
        )
        .layer(TraceLayer::new_for_http())
        .layer(CorsLayer::permissive())
        .with_state(state)
}

/// Full server bootstrap: tracing, config, database, and a graceful-shutdown serve loop.
pub async fn run() {
    init_tracing();

    let config = Config::from_env();
    let addr = config.addr.clone();
    let state = build_state(config)
        .await
        .expect("failed to initialize database");
    let app = router(state);

    let listener = TcpListener::bind(&addr)
        .await
        .unwrap_or_else(|e| panic!("failed to bind {addr}: {e}"));
    tracing::info!("quits-server listening on http://{addr}");

    axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal())
        .await
        .expect("server error");
}

fn init_tracing() {
    use tracing_subscriber::EnvFilter;
    // `try_init` so repeated calls (e.g. in tests) don't panic.
    tracing_subscriber::fmt()
        .with_env_filter(
            EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| "quits_server=info,tower_http=info".into()),
        )
        .init();
}

/// Resolves on Ctrl-C or SIGTERM so the relay can drain in-flight requests.
async fn shutdown_signal() {
    let ctrl_c = async {
        signal::ctrl_c().await.expect("install Ctrl-C handler");
    };

    #[cfg(unix)]
    let terminate = async {
        signal::unix::signal(signal::unix::SignalKind::terminate())
            .expect("install SIGTERM handler")
            .recv()
            .await;
    };

    #[cfg(not(unix))]
    let terminate = std::future::pending::<()>();

    tokio::select! {
        _ = ctrl_c => {},
        _ = terminate => {},
    }
}
