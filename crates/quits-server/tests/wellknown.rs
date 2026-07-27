//! Deep-link support: the association files and `/join` landing that make invite links open the
//! app (App Links / Universal Links) or fall back to the web app.

use axum::Router;
use axum::body::Body;
use axum::http::{Request, StatusCode, header};
use http_body_util::BodyExt;
use tower::ServiceExt; // oneshot
use uuid::Uuid;

use quits_server::config::Config;
use quits_server::{build_state, router};

fn test_config() -> Config {
    let path = std::env::temp_dir().join(format!("quits-test-{}.db", Uuid::new_v4()));
    Config::for_test(format!("sqlite:{}", path.display()))
}

async fn get(app: &Router, uri: &str) -> (StatusCode, String, String) {
    let request = Request::builder()
        .method("GET")
        .uri(uri)
        .body(Body::empty())
        .unwrap();
    let response = app.clone().oneshot(request).await.unwrap();
    let status = response.status();
    let content_type = response
        .headers()
        .get(header::CONTENT_TYPE)
        .and_then(|v| v.to_str().ok())
        .unwrap_or_default()
        .to_string();
    let bytes = response.into_body().collect().await.unwrap().to_bytes();
    (status, content_type, String::from_utf8(bytes.to_vec()).unwrap())
}

#[tokio::test]
async fn assetlinks_advertises_the_configured_fingerprint() {
    let config = test_config();
    let expected = config.android_cert_sha256.clone();
    let app = router(build_state(config).await.unwrap());

    let (status, content_type, body) = get(&app, "/.well-known/assetlinks.json").await;
    assert_eq!(status, StatusCode::OK);
    assert!(content_type.starts_with("application/json"), "{content_type}");
    assert!(body.contains("nz.eloque.quits"), "{body}");
    for fingerprint in &expected {
        assert!(body.contains(fingerprint), "{body}");
    }
}

#[tokio::test]
async fn assetlinks_lists_every_configured_fingerprint() {
    let mut config = test_config();
    config.android_cert_sha256 = vec!["AA:BB".to_string(), "CC:DD".to_string()];
    let app = router(build_state(config).await.unwrap());

    let (status, _content_type, body) = get(&app, "/.well-known/assetlinks.json").await;
    assert_eq!(status, StatusCode::OK);
    assert!(body.contains("AA:BB"), "{body}");
    assert!(body.contains("CC:DD"), "{body}");
}

#[tokio::test]
async fn aasa_is_json_with_the_app_id_and_join_path() {
    let config = test_config();
    let expected = config.ios_app_id.clone();
    let app = router(build_state(config).await.unwrap());

    // Apple requires application/json, served without a redirect, at the extensionless path.
    let (status, content_type, body) = get(&app, "/.well-known/apple-app-site-association").await;
    assert_eq!(status, StatusCode::OK);
    assert!(content_type.starts_with("application/json"), "{content_type}");
    assert!(body.contains(&expected), "{body}");
    assert!(body.contains("/join*"), "{body}");
}

#[tokio::test]
async fn join_landing_is_html_and_hands_off_to_the_web_app() {
    let app = router(build_state(test_config()).await.unwrap());

    let (status, content_type, body) = get(&app, "/join").await;
    assert_eq!(status, StatusCode::OK);
    assert!(content_type.starts_with("text/html"), "{content_type}");
    // Forwards to the web app carrying the fragment client-side (the secret never reaches us).
    assert!(body.contains("app.quits.eloque.nz"), "{body}");
    assert!(body.contains("location.hash"), "{body}");
}
