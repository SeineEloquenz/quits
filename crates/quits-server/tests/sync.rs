//! End-to-end tests for the relay: proof-of-work-gated creation, join, and last-write-wins sync
//! convergence between two "devices" sharing one in-process app.

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
use quits_server::{build_state, router};

fn test_config() -> Config {
    let path = std::env::temp_dir().join(format!("quits-test-{}.db", Uuid::new_v4()));
    Config::for_test(format!("sqlite:{}", path.display()))
}

async fn app_with(config: Config) -> Router {
    let state = build_state(config).await.expect("build state");
    router(state)
}

async fn send(
    app: &Router,
    method: &str,
    uri: &str,
    token: Option<&str>,
    body: Option<Value>,
) -> (StatusCode, Value) {
    let mut builder = Request::builder().method(method).uri(uri);
    if let Some(t) = token {
        builder = builder.header(header::AUTHORIZATION, format!("Bearer {t}"));
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

/// Creates a group. Returns `(group_id, code, token)`.
async fn create_group(app: &Router) -> (String, String, String) {
    let (status, resp) = send(app, "POST", "/v1/groups", None, None).await;
    assert_eq!(status, StatusCode::OK, "create failed: {resp:?}");
    (
        resp["group_id"].as_str().unwrap().to_string(),
        resp["code"].as_str().unwrap().to_string(),
        resp["token"].as_str().unwrap().to_string(),
    )
}

fn record(id: &str, updated_at: i64, device: &str, payload: &str) -> Value {
    json!({
        "id": id,
        "updated_at": updated_at,
        "device_id": device,
        "payload": B64.encode(payload),
    })
}

async fn push(app: &Router, gid: &str, token: &str, records: Vec<Value>) -> Value {
    let (status, resp) = send(
        app,
        "POST",
        &format!("/v1/groups/{gid}/changes"),
        Some(token),
        Some(json!({ "records": records })),
    )
    .await;
    assert_eq!(status, StatusCode::OK, "push failed: {resp:?}");
    resp
}

async fn pull(app: &Router, gid: &str, token: &str, since: i64) -> Value {
    let (status, resp) = send(
        app,
        "GET",
        &format!("/v1/groups/{gid}/changes?since={since}"),
        Some(token),
        None,
    )
    .await;
    assert_eq!(status, StatusCode::OK, "pull failed: {resp:?}");
    resp
}

fn payload_of(record: &Value) -> String {
    String::from_utf8(B64.decode(record["payload"].as_str().unwrap()).unwrap()).unwrap()
}

fn ids(list: &Value) -> Vec<String> {
    list.as_array()
        .unwrap()
        .iter()
        .map(|v| v.as_str().unwrap().to_string())
        .collect()
}

#[tokio::test]
async fn two_devices_converge_with_last_write_wins() {
    let app = app_with(test_config()).await;
    let (gid, code, token_a) = create_group(&app).await;

    // Device B joins by code and gets a token for the same group.
    let (status, joined) = send(
        &app,
        "POST",
        "/v1/groups/join",
        None,
        Some(json!({ "code": code })),
    )
    .await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(joined["group_id"].as_str().unwrap(), gid);
    let token_b = joined["token"].as_str().unwrap().to_string();

    // A creates r1 = "v1".
    let resp = push(&app, &gid, &token_a, vec![record("r1", 100, "A", "v1")]).await;
    assert_eq!(ids(&resp["applied"]), vec!["r1"]);

    // B pulls from scratch and sees it.
    let resp = pull(&app, &gid, &token_b, 0).await;
    assert_eq!(resp["records"].as_array().unwrap().len(), 1);
    assert_eq!(payload_of(&resp["records"][0]), "v1");

    // B updates r1 = "v2" with a newer clock.
    push(&app, &gid, &token_b, vec![record("r1", 200, "B", "v2")]).await;

    // A pulls and converges to "v2".
    let resp = pull(&app, &gid, &token_a, 0).await;
    assert_eq!(payload_of(&resp["records"][0]), "v2");

    // A pushes a STALE update (older clock) — it must be rejected, state unchanged.
    let resp = push(&app, &gid, &token_a, vec![record("r1", 150, "A", "stale")]).await;
    assert_eq!(ids(&resp["rejected"]), vec!["r1"]);
    assert!(resp["applied"].as_array().unwrap().is_empty());
    let resp = pull(&app, &gid, &token_b, 0).await;
    assert_eq!(payload_of(&resp["records"][0]), "v2");

    // Tombstone delete propagates.
    let tombstone =
        json!({ "id": "r1", "updated_at": 300, "device_id": "A", "deleted": true, "payload": "" });
    push(&app, &gid, &token_a, vec![tombstone]).await;
    let resp = pull(&app, &gid, &token_b, 0).await;
    assert!(resp["records"][0]["deleted"].as_bool().unwrap());
}

#[tokio::test]
async fn changes_require_a_matching_group_token() {
    let app = app_with(test_config()).await;
    let (gid, _code, _token) = create_group(&app).await;

    // No token → 401.
    let (status, _) = send(
        &app,
        "GET",
        &format!("/v1/groups/{gid}/changes?since=0"),
        None,
        None,
    )
    .await;
    assert_eq!(status, StatusCode::UNAUTHORIZED);

    // A valid token for a *different* group → 403.
    let (_gid2, _code2, token2) = create_group(&app).await;
    let (status, _) = send(
        &app,
        "GET",
        &format!("/v1/groups/{gid}/changes?since=0"),
        Some(&token2),
        None,
    )
    .await;
    assert_eq!(status, StatusCode::FORBIDDEN);
}
