use axum::Router;
use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use serde_json::json;
use tower::ServiceExt; // oneshot
use uuid::Uuid;

use quits_server::client::VERSION_HEADER;
use quits_server::config::Config;
use quits_server::state::AppState;
use quits_server::{build_state, router};

fn test_config() -> Config {
    let path = std::env::temp_dir().join(format!("quits-test-{}.db", Uuid::new_v4()));
    Config::for_test(format!("sqlite:{}", path.display()))
}

/// The exporter is only attached when an endpoint is configured; nothing binds until `serve`.
async fn state_with_metrics() -> AppState {
    let mut config = test_config();
    config.metrics_addr = Some("127.0.0.1:0".to_string());
    build_state(config).await.expect("build state")
}

async fn create_as(app: &Router, version: Option<&str>) -> StatusCode {
    let mut builder = Request::builder()
        .method("POST")
        .uri("/v1/groups")
        .header(header::CONTENT_TYPE, "application/json");
    if let Some(version) = version {
        builder = builder.header(VERSION_HEADER, version);
    }
    let body = json!({ "lookup_id": Uuid::new_v4().to_string() });
    let request = builder
        .body(Body::from(serde_json::to_vec(&body).unwrap()))
        .unwrap();
    app.clone().oneshot(request).await.unwrap().status()
}

#[tokio::test]
async fn the_shipped_relay_serves_every_version() {
    let app = router(build_state(test_config()).await.expect("build state"));

    assert_eq!(create_as(&app, Some("0.1.0")).await, StatusCode::OK);
    assert_eq!(create_as(&app, Some("0.10.1")).await, StatusCode::OK);
    assert_eq!(create_as(&app, None).await, StatusCode::OK);
}

#[tokio::test]
async fn requests_are_counted_by_reported_version() {
    let state = state_with_metrics().await;
    let app = router(state.clone());

    create_as(&app, Some("0.10.1")).await;
    create_as(&app, Some("0.10.4")).await;
    create_as(&app, Some("0.11.0")).await;

    let exported = state.metrics.encode();
    assert!(
        exported.contains(r#"quits_client_requests_total{version="0.10""#),
        "expected a 0.10 series in:\n{exported}"
    );
    assert!(
        exported.contains(r#"quits_client_requests_total{version="0.11""#),
        "expected a 0.11 series in:\n{exported}"
    );
}

#[tokio::test]
async fn callers_reporting_no_version_share_one_series() {
    let state = state_with_metrics().await;
    let app = router(state.clone());

    create_as(&app, None).await;
    create_as(&app, Some("nightly")).await;

    let exported = state.metrics.encode();
    assert!(
        exported.contains(r#"quits_client_requests_total{version="unknown""#),
        "expected a catch-all series in:\n{exported}"
    );
    assert!(
        !exported.contains(r#"version="nightly"#),
        "an unparseable version must not get its own series:\n{exported}"
    );
}

#[tokio::test]
async fn info_is_not_gated_or_counted() {
    let state = state_with_metrics().await;
    let app = router(state.clone());

    let request = Request::builder()
        .uri("/v1/info")
        .header(VERSION_HEADER, "0.1.0")
        .body(Body::empty())
        .unwrap();
    let status = app.oneshot(request).await.unwrap().status();

    assert_eq!(status, StatusCode::OK);
    assert!(
        !state
            .metrics
            .encode()
            .contains("quits_client_requests_total"),
        "info sits outside the version layer so a refused client can still read the policy"
    );
}
