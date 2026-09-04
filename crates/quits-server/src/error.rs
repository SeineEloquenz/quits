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

    #[error("server at capacity")]
    Capacity,

    #[error("record payload exceeds the size limit")]
    RecordTooLarge(Vec<String>),

    #[error("group has reached its record limit")]
    GroupFull,

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
            AppError::Capacity => StatusCode::SERVICE_UNAVAILABLE,
            AppError::RecordTooLarge(_) => StatusCode::PAYLOAD_TOO_LARGE,
            AppError::GroupFull => StatusCode::INSUFFICIENT_STORAGE,
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

        (status, Json(body)).into_response()
    }
}

pub type AppResult<T> = Result<T, AppError>;
