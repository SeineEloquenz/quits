//! Group access tokens.
//!
//! Creating or joining a group exchanges the group `code` once for a JWT scoped to that group's
//! id. Every `/v1/groups/{id}/*` request carries it as `Authorization: Bearer <jwt>`, and the
//! handler checks `claims.gid == {id}`.

use axum::extract::FromRequestParts;
use axum::http::header::AUTHORIZATION;
use axum::http::request::Parts;
use jsonwebtoken::{Algorithm, DecodingKey, EncodingKey, Header, Validation, decode, encode};
use serde::{Deserialize, Serialize};

use crate::error::AppError;
use crate::state::AppState;

#[derive(Debug, Serialize, Deserialize)]
pub struct Claims {
    /// Group id this token grants access to.
    pub gid: String,
    /// Issued-at (epoch seconds).
    pub iat: u64,
    /// Expiry (epoch seconds).
    pub exp: u64,
}

pub fn issue_token(secret: &[u8], gid: String, now_secs: u64, ttl_secs: u64) -> String {
    let claims = Claims {
        gid,
        iat: now_secs,
        exp: now_secs + ttl_secs,
    };
    encode(
        &Header::new(Algorithm::HS256),
        &claims,
        &EncodingKey::from_secret(secret),
    )
    .expect("HS256 JWT encoding of serializable claims is infallible")
}

pub fn verify_token(secret: &[u8], token: &str) -> Result<Claims, AppError> {
    let mut validation = Validation::new(Algorithm::HS256);
    validation.validate_aud = false;
    decode::<Claims>(token, &DecodingKey::from_secret(secret), &validation)
        .map(|data| data.claims)
        .map_err(|_| AppError::Unauthorized)
}

/// Validates the `Authorization: Bearer <jwt>` header into the token claims.
pub struct GroupToken(pub Claims);

impl FromRequestParts<AppState> for GroupToken {
    type Rejection = AppError;

    async fn from_request_parts(
        parts: &mut Parts,
        state: &AppState,
    ) -> Result<Self, Self::Rejection> {
        let token = parts
            .headers
            .get(AUTHORIZATION)
            .and_then(|v| v.to_str().ok())
            .and_then(|v| v.strip_prefix("Bearer "))
            .ok_or(AppError::Unauthorized)?;
        let claims = verify_token(&state.config.jwt_secret, token)?;
        Ok(GroupToken(claims))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn token_round_trips() {
        let secret = b"k";
        // Real `now` so the token's `exp` is in the future (jsonwebtoken validates exp by default).
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_secs();
        let token = issue_token(secret, "group-123".to_string(), now, 3_600);
        let claims = verify_token(secret, &token).expect("valid");
        assert_eq!(claims.gid, "group-123");
    }

    #[test]
    fn rejects_wrong_secret() {
        let token = issue_token(b"right", "g".to_string(), 1_000, 3_600);
        assert!(verify_token(b"wrong", &token).is_err());
    }
}
