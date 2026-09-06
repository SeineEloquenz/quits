//! Application error type and its HTTP mapping.

use axum::Json;
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use serde_json::json;

#[derive(Debug, thiserror::Error)]
pub enum AppError {
    #[error("bad request: {0}")]
    BadRequest(String),

    #[error("unauthorized")]
    Unauthorized,

    #[error("forbidden")]
    Forbidden,

    #[error("not found")]
    NotFound,

    #[error("server has no room for more groups")]
    InstanceFull,

    #[error("record payload exceeds the size limit")]
    RecordTooLarge(Vec<String>),

    #[error("group has reached its record limit")]
    GroupFull,

    #[error("client older than the minimum this relay accepts ({0})")]
    ClientTooOld(String),

    #[error("internal error: {0}")]
    Internal(String),

    #[error(transparent)]
    Database(#[from] sqlx::Error),
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let status = match &self {
            AppError::BadRequest(_) => StatusCode::BAD_REQUEST,
            AppError::Unauthorized => StatusCode::UNAUTHORIZED,
            AppError::Forbidden => StatusCode::FORBIDDEN,
            AppError::NotFound => StatusCode::NOT_FOUND,
            AppError::InstanceFull => StatusCode::INSUFFICIENT_STORAGE,
            AppError::RecordTooLarge(_) => StatusCode::PAYLOAD_TOO_LARGE,
            AppError::GroupFull => StatusCode::INSUFFICIENT_STORAGE,
            AppError::ClientTooOld(_) => StatusCode::UPGRADE_REQUIRED,
            AppError::Internal(e) => {
                // Internal details are logged, never returned to the client.
                tracing::error!("internal error: {e}");
                StatusCode::INTERNAL_SERVER_ERROR
            }
            AppError::Database(e) => {
                // Internal details are logged, never returned to the client.
                tracing::error!("database error: {e}");
                StatusCode::INTERNAL_SERVER_ERROR
            }
        };

        let message = match &self {
            AppError::Database(_) | AppError::Internal(_) => "internal error".to_string(),
            other => other.to_string(),
        };

        let mut body = json!({ "error": message });
        if let AppError::RecordTooLarge(ids) = &self {
            body["records"] = json!(ids);
        }
        if let AppError::ClientTooOld(min) = &self {
            body["min_version"] = json!(min);
        }

        (status, Json(body)).into_response()
    }
}

pub type AppResult<T> = Result<T, AppError>;

#[cfg(test)]
mod tests {
    use axum::body::to_bytes;

    use super::*;

    #[tokio::test]
    async fn client_too_old_names_the_minimum_in_its_own_field() {
        let response = AppError::ClientTooOld("0.11.0".to_string()).into_response();
        assert_eq!(response.status(), StatusCode::UPGRADE_REQUIRED);

        let bytes = to_bytes(response.into_body(), usize::MAX)
            .await
            .expect("body");
        let body: serde_json::Value = serde_json::from_slice(&bytes).expect("json");

        assert_eq!(body["min_version"], "0.11.0");
        assert!(
            body["error"].as_str().is_some_and(|e| e.contains("0.11.0")),
            "the prose should name it too, for anyone reading the raw reply: {body}"
        );
    }
}
