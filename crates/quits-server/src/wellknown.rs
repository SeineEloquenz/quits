//! Deep-link support served from the invite-link domain (`quits.eloque.nz`).
//!
//! Invite links look like `https://quits.eloque.nz/join#<secret>`. The secret rides in the URL
//! **fragment**, so it never reaches these handlers (or any log). We serve:
//!
//! - the association files that let Android App Links / iOS Universal Links open the app directly,
//! - a `/join` landing page that hands off to the web app (preserving the fragment) for anyone
//!   without the app installed.
//!
//! The reverse proxy fronting `quits.eloque.nz` must forward `/.well-known/*` and `/join` here.

use axum::Json;
use axum::extract::State;
use axum::http::header;
use axum::response::{Html, IntoResponse};
use serde_json::json;

use crate::state::AppState;

/// The web app the `/join` landing hands off to when the native app isn't installed.
const WEB_APP_ORIGIN: &str = "https://app.quits.eloque.nz";

/// `GET /.well-known/assetlinks.json` — Digital Asset Links for verified Android App Links.
pub async fn assetlinks(State(state): State<AppState>) -> Json<serde_json::Value> {
    Json(json!([{
        "relation": ["delegate_permission/common.handle_all_urls"],
        "target": {
            "namespace": "android_app",
            "package_name": "nz.eloque.quits",
            "sha256_cert_fingerprints": state.config.android_cert_sha256,
        },
    }]))
}

/// `GET /.well-known/apple-app-site-association` — must be `application/json`, served without a
/// redirect. `Json` sets the content type; the extensionless path is fine.
pub async fn apple_app_site_association(State(state): State<AppState>) -> Json<serde_json::Value> {
    Json(json!({
        "applinks": {
            "apps": [],
            "details": [{
                "appID": state.config.ios_app_id,
                "paths": ["/join*"],
            }],
        },
    }))
}

/// `GET /join` — the App Link / Universal Link target. When the app is installed the OS opens it
/// directly and this HTML is never seen; otherwise the browser lands here and JS forwards to the
/// web app, carrying the fragment (and thus the secret) client-side only.
pub async fn join_landing() -> impl IntoResponse {
    let body = format!(
        r#"<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Quits invite</title>
</head>
<body>
<p>Opening Quits&hellip;</p>
<p><a id="open" href="{origin}/">Open Quits</a></p>
<script>
  var target = "{origin}/" + window.location.hash;
  document.getElementById("open").href = target;
  window.location.replace(target);
</script>
</body>
</html>
"#,
        origin = WEB_APP_ORIGIN,
    );
    ([(header::CACHE_CONTROL, "no-store")], Html(body))
}
