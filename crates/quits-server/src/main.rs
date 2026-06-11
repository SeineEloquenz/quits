//! Quits sync relay.
//!
//! A deliberately *dumb*, domain-agnostic sync relay: it stores opaque records per group and
//! relays deltas. It knows nothing about expenses, splits, members, or money — all of that lives
//! client-side. This groundwork keeps the door open for end-to-end encryption later (the server
//! only ever sees ciphertext + minimal routing metadata).
//!
//! Phase 1 ships only the scaffold: a `/health` endpoint and graceful shutdown. The records
//! store, group create/join, proof-of-work gate, and `/changes` sync endpoints arrive in phase 2.

use axum::{Router, routing::get};
use tokio::net::TcpListener;
use tokio::signal;

#[tokio::main]
async fn main() {
    init_tracing();

    let app = router();

    let addr = std::env::var("QUITS_ADDR").unwrap_or_else(|_| "0.0.0.0:8080".to_string());
    let listener = TcpListener::bind(&addr)
        .await
        .unwrap_or_else(|e| panic!("failed to bind {addr}: {e}"));

    tracing::info!("quits-server listening on http://{addr}");
    axum::serve(listener, app)
        .with_graceful_shutdown(shutdown_signal())
        .await
        .expect("server error");
}

/// Builds the application router. Kept separate from `main` so it is unit-testable.
fn router() -> Router {
    Router::new().route("/health", get(health))
}

/// Liveness probe.
async fn health() -> &'static str {
    "ok"
}

fn init_tracing() {
    use tracing_subscriber::EnvFilter;
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

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn health_returns_ok() {
        assert_eq!(health().await, "ok");
    }

    #[test]
    fn router_builds() {
        // Building the router must not panic.
        let _ = router();
    }
}
