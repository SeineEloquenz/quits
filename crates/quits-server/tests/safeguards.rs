//! Tests for the public-instance safeguards: global group cap, payload/storage quotas,
//! per-IP rate limiting on creation, and the stale-group reaper.

use axum::Router;
use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use base64::Engine as _;
use base64::engine::general_purpose::STANDARD as B64;
use http_body_util::BodyExt;
use serde_json::{Value, json};
use tower::ServiceExt; // oneshot
use uuid::Uuid;

use quits_server::config::Config;
use quits_server::{build_state, reaper, router};

fn test_config() -> Config {
    let path = std::env::temp_dir().join(format!("quits-test-{}.db", Uuid::new_v4()));
    Config::for_test(format!("sqlite:{}", path.display()))
}

async fn state_with(config: Config) -> quits_server::state::AppState {
    build_state(config).await.expect("build state")
}

/// Sends a request with an optional `X-Real-IP` header (used to simulate distinct clients).
async fn send(
    app: &Router,
    method: &str,
    uri: &str,
    token: Option<&str>,
    real_ip: Option<&str>,
    body: Option<Value>,
) -> (StatusCode, Value) {
    let mut builder = Request::builder().method(method).uri(uri);
    if let Some(t) = token {
        builder = builder.header(header::AUTHORIZATION, format!("Bearer {t}"));
    }
    if let Some(ip) = real_ip {
        builder = builder.header("X-Real-IP", ip);
    }
    let request = match body {
        Some(b) => builder
            .header(header::CONTENT_TYPE, "application/json")
            .body(Body::from(serde_json::to_vec(&b).unwrap()))
            .unwrap(),
        None => builder.body(Body::empty()).unwrap(),
    };
    let response = app.clone().oneshot(request).await.unwrap();
    let status = response.status();
    let bytes = response.into_body().collect().await.unwrap().to_bytes();
    let value = serde_json::from_slice(&bytes).unwrap_or(Value::Null);
    (status, value)
}

async fn create(app: &Router, real_ip: Option<&str>) -> (StatusCode, Value) {
    send(
        app,
        "POST",
        "/v1/groups",
        None,
        real_ip,
        Some(json!({ "lookup_id": Uuid::new_v4().to_string() })),
    )
    .await
}

fn record(id: &str, updated_at: i64, device: &str, payload: &str) -> Value {
    json!({
        "id": id,
        "updated_at": updated_at,
        "device_id": device,
        "payload": B64.encode(payload),
    })
}

#[tokio::test]
async fn global_group_cap_returns_507_when_full() {
    let mut config = test_config();
    config.max_groups = 1;
    let app = router(state_with(config).await);

    let (status, _) = create(&app, None).await;
    assert_eq!(status, StatusCode::OK);

    let (status, _) = create(&app, None).await;
    assert_eq!(status, StatusCode::INSUFFICIENT_STORAGE);
}

#[tokio::test]
async fn oversized_record_fails_the_whole_push() {
    let mut config = test_config();
    config.max_record_bytes = 16;
    let app = router(state_with(config).await);

    let (_status, created) = create(&app, None).await;
    let gid = created["group_id"].as_str().unwrap();
    let token = created["token"].as_str().unwrap();

    let (status, resp) = send(
        &app,
        "POST",
        &format!("/v1/groups/{gid}/changes"),
        Some(token),
        None,
        Some(json!({ "records": [
            record("small", 100, "A", "tiny"),
            record("big", 100, "A", "this payload is definitely longer than sixteen bytes"),
        ]})),
    )
    .await;

    assert_eq!(status, StatusCode::PAYLOAD_TOO_LARGE);
    assert_eq!(resp["records"], json!(["big"]));

    // The acceptable record in the same batch must not have been stored either
    let (status, resp) = send(
        &app,
        "GET",
        &format!("/v1/groups/{gid}/changes?since=0"),
        Some(token),
        None,
        None,
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(resp["records"], json!([]));
}

#[tokio::test]
async fn new_records_past_the_group_cap_fail_the_push() {
    let mut config = test_config();
    config.max_records_per_group = 2;
    let app = router(state_with(config).await);

    let (_status, created) = create(&app, None).await;
    let gid = created["group_id"].as_str().unwrap().to_string();
    let token = created["token"].as_str().unwrap().to_string();

    let (_s, resp) = send(
        &app,
        "POST",
        &format!("/v1/groups/{gid}/changes"),
        Some(&token),
        None,
        Some(json!({ "records": [
            record("r1", 100, "A", "a"),
            record("r2", 100, "A", "b"),
        ]})),
    )
    .await;
    assert_eq!(resp["applied"], json!(["r1", "r2"]));

    let (status, _resp) = send(
        &app,
        "POST",
        &format!("/v1/groups/{gid}/changes"),
        Some(&token),
        None,
        Some(json!({ "records": [ record("r3", 200, "A", "c") ]})),
    )
    .await;
    assert_eq!(status, StatusCode::INSUFFICIENT_STORAGE);

    let (_s, resp) = send(
        &app,
        "GET",
        &format!("/v1/groups/{gid}/changes?since=0"),
        Some(&token),
        None,
        None,
    )
    .await;
    assert_eq!(resp["records"].as_array().unwrap().len(), 2);
}

#[tokio::test]
async fn updates_still_apply_to_a_full_group() {
    let mut config = test_config();
    config.max_records_per_group = 2;
    let app = router(state_with(config).await);

    let (_status, created) = create(&app, None).await;
    let gid = created["group_id"].as_str().unwrap().to_string();
    let token = created["token"].as_str().unwrap().to_string();

    send(
        &app,
        "POST",
        &format!("/v1/groups/{gid}/changes"),
        Some(&token),
        None,
        Some(json!({ "records": [
            record("r1", 100, "A", "a"),
            record("r2", 100, "A", "b"),
        ]})),
    )
    .await;

    let (status, resp) = send(
        &app,
        "POST",
        &format!("/v1/groups/{gid}/changes"),
        Some(&token),
        None,
        Some(json!({ "records": [
            record("r1", 200, "A", "a2"),
            record("r2", 200, "A", "b2"),
        ]})),
    )
    .await;

    assert_eq!(status, StatusCode::OK);
    assert_eq!(resp["applied"], json!(["r1", "r2"]));
    assert_eq!(resp["rejected"], json!([]));
}

#[tokio::test]
async fn last_write_wins_loser_stays_a_success() {
    let app = router(state_with(test_config()).await);

    let (_status, created) = create(&app, None).await;
    let gid = created["group_id"].as_str().unwrap().to_string();
    let token = created["token"].as_str().unwrap().to_string();

    send(
        &app,
        "POST",
        &format!("/v1/groups/{gid}/changes"),
        Some(&token),
        None,
        Some(json!({ "records": [ record("r1", 200, "A", "new") ]})),
    )
    .await;

    let (status, resp) = send(
        &app,
        "POST",
        &format!("/v1/groups/{gid}/changes"),
        Some(&token),
        None,
        Some(json!({ "records": [ record("r1", 100, "A", "old") ]})),
    )
    .await;

    assert_eq!(status, StatusCode::OK);
    assert_eq!(resp["applied"], json!([]));
    assert_eq!(resp["rejected"], json!(["r1"]));
}

#[tokio::test]
async fn request_body_limit_returns_413() {
    let mut config = test_config();
    config.max_body_bytes = 256;
    let app = router(state_with(config).await);

    let (_status, created) = create(&app, None).await;
    let gid = created["group_id"].as_str().unwrap();
    let token = created["token"].as_str().unwrap();

    let big = "x".repeat(4096);
    let (status, _) = send(
        &app,
        "POST",
        &format!("/v1/groups/{gid}/changes"),
        Some(token),
        None,
        Some(json!({ "records": [record("r1", 100, "A", &big)] })),
    )
    .await;
    assert_eq!(status, StatusCode::PAYLOAD_TOO_LARGE);
}

#[tokio::test]
async fn create_rate_limit_is_per_ip() {
    let mut config = test_config();
    config.behind_proxy = true;
    config.create_burst = 2;
    config.create_replenish_secs = 600; // slow enough that the bucket won't refill mid-test
    let app = router(state_with(config).await);

    // Two creates from one IP succeed, the third is throttled.
    assert_eq!(create(&app, Some("1.1.1.1")).await.0, StatusCode::OK);
    assert_eq!(create(&app, Some("1.1.1.1")).await.0, StatusCode::OK);
    assert_eq!(
        create(&app, Some("1.1.1.1")).await.0,
        StatusCode::TOO_MANY_REQUESTS
    );

    // A different IP has its own bucket.
    assert_eq!(create(&app, Some("2.2.2.2")).await.0, StatusCode::OK);
}

#[tokio::test]
async fn reaper_removes_empty_and_inactive_groups_only() {
    let mut config = test_config();
    config.empty_group_ttl_secs = 3600; // 1h
    config.inactive_group_ttl_secs = 86_400; // 1d
    let state = state_with(config).await;

    // Timestamps are relative to the real clock, since the reaper compares against `now`.
    let now = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap()
        .as_millis() as i64;
    let old_created = now - 10 * 3600 * 1000; // 10h old
    let old_activity = now - 5 * 86_400 * 1000; // 5d old

    // Old + empty → reaped by the empty rule.
    sqlx::query("INSERT INTO groups (id, lookup_id, created_at) VALUES ('empty_old', 'l1', ?)")
        .bind(old_created)
        .execute(&state.db)
        .await
        .unwrap();
    // Fresh + empty → kept.
    sqlx::query("INSERT INTO groups (id, lookup_id, created_at) VALUES ('empty_new', 'l2', ?)")
        .bind(now)
        .execute(&state.db)
        .await
        .unwrap();
    // Old records → reaped by the inactive rule.
    sqlx::query("INSERT INTO groups (id, lookup_id, created_at) VALUES ('stale', 'l3', ?)")
        .bind(old_created)
        .execute(&state.db)
        .await
        .unwrap();
    sqlx::query(
        "INSERT INTO records (group_id, id, updated_at, deleted, device_id, payload, server_seq)
         VALUES ('stale', 'r1', ?, 0, 'A', X'00', 1)",
    )
    .bind(old_activity)
    .execute(&state.db)
    .await
    .unwrap();
    // Recent records → kept.
    sqlx::query("INSERT INTO groups (id, lookup_id, created_at) VALUES ('active', 'l4', ?)")
        .bind(old_created)
        .execute(&state.db)
        .await
        .unwrap();
    sqlx::query(
        "INSERT INTO records (group_id, id, updated_at, deleted, device_id, payload, server_seq)
         VALUES ('active', 'r1', ?, 0, 'A', X'00', 2)",
    )
    .bind(now)
    .execute(&state.db)
    .await
    .unwrap();

    let stats = reaper::reap_once(&state.db, &state.config).await.unwrap();
    assert_eq!(stats.empty, 1);
    assert_eq!(stats.inactive, 1);

    let survivors: Vec<(String,)> = sqlx::query_as("SELECT id FROM groups ORDER BY id")
        .fetch_all(&state.db)
        .await
        .unwrap();
    let ids: Vec<&str> = survivors.iter().map(|(s,)| s.as_str()).collect();
    assert_eq!(ids, vec!["active", "empty_new"]);
}
