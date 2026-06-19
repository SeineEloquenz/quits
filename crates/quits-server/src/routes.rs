//! HTTP handlers. The relay is domain-agnostic: it authorizes by group token, stores opaque
//! record payloads, and reconciles them with last-write-wins. It never parses a payload.

use std::convert::Infallible;
use std::time::{SystemTime, UNIX_EPOCH};

use axum::Json;
use axum::extract::{FromRequestParts, Path, Query, State};
use axum::http::request::Parts;
use base64::Engine as _;
use base64::engine::general_purpose::STANDARD as B64;
use serde::{Deserialize, Serialize};
use uuid::Uuid;

use crate::auth::{Claims, GroupToken, issue_token};
use crate::error::{AppError, AppResult};
use crate::state::AppState;

/// Join/share codes use an unambiguous 31-char alphabet (no 0/O/1/I/L).
const CODE_ALPHABET: &[u8] = b"ABCDEFGHJKMNPQRSTUVWXYZ23456789";
const CODE_LEN: usize = 9;

pub struct ClientContext {
    pub instance_header: Option<String>,
}

impl FromRequestParts<AppState> for ClientContext {
    type Rejection = Infallible;

    async fn from_request_parts(parts: &mut Parts, _: &AppState) -> Result<Self, Self::Rejection> {
        let instance_header = parts
            .headers
            .get("x-quits-instance")
            .and_then(|v| v.to_str().ok())
            .map(str::to_string);
        Ok(ClientContext { instance_header })
    }
}

#[derive(Debug, Serialize)]
pub struct CreateGroupResponse {
    pub group_id: String,
    pub code: String,
    pub token: String,
}

#[derive(Debug, Deserialize)]
pub struct JoinGroupRequest {
    pub code: String,
}

#[derive(Debug, Serialize)]
pub struct JoinGroupResponse {
    pub group_id: String,
    pub token: String,
}

#[derive(Debug, Deserialize)]
pub struct RecordIn {
    pub id: String,
    pub updated_at: i64,
    #[serde(default)]
    pub deleted: bool,
    pub device_id: String,
    /// Opaque payload, base64-encoded in transit.
    pub payload: String,
}

#[derive(Debug, Serialize)]
pub struct RecordOut {
    pub id: String,
    pub updated_at: i64,
    pub deleted: bool,
    pub device_id: String,
    pub payload: String,
    pub server_seq: i64,
}

#[derive(Debug, Deserialize)]
pub struct PushRequest {
    pub records: Vec<RecordIn>,
}

#[derive(Debug, Serialize)]
pub struct PushResponse {
    pub seq: i64,
    pub applied: Vec<String>,
    pub rejected: Vec<String>,
}

#[derive(Debug, Serialize)]
pub struct PullResponse {
    pub records: Vec<RecordOut>,
    pub seq: i64,
}

#[derive(Debug, Deserialize)]
pub struct ChangesQuery {
    #[serde(default)]
    pub since: i64,
}

#[derive(sqlx::FromRow)]
struct RecordRow {
    id: String,
    updated_at: i64,
    deleted: i64,
    device_id: String,
    payload: Vec<u8>,
    server_seq: i64,
}

impl RecordRow {
    fn into_out(self) -> RecordOut {
        RecordOut {
            id: self.id,
            updated_at: self.updated_at,
            deleted: self.deleted != 0,
            device_id: self.device_id,
            payload: B64.encode(&self.payload),
            server_seq: self.server_seq,
        }
    }
}

pub async fn health() -> &'static str {
    "ok"
}

/// Creates a group. Gated by the optional instance secret.
pub async fn create_group(
    State(state): State<AppState>,
    ctx: ClientContext,
) -> AppResult<Json<CreateGroupResponse>> {
    if let Some(secret) = &state.config.instance_secret
        && ctx.instance_header.as_deref() != Some(secret.as_str())
    {
        return Err(AppError::Forbidden);
    }

    let id = Uuid::new_v4().to_string();
    let created_at = now_ms() as i64;

    // Retry on the (astronomically unlikely) chance of a share-code collision, regenerating the
    // code each time. `code` always holds the value that was actually inserted on success.
    const MAX_CODE_ATTEMPTS: usize = 5;
    let mut code = generate_code();
    let mut inserted = false;
    for _ in 0..MAX_CODE_ATTEMPTS {
        match sqlx::query("INSERT INTO groups (id, code, created_at) VALUES (?, ?, ?)")
            .bind(&id)
            .bind(&code)
            .bind(created_at)
            .execute(&state.db)
            .await
        {
            Ok(_) => {
                inserted = true;
                break;
            }
            Err(sqlx::Error::Database(e)) if e.is_unique_violation() => {
                code = generate_code();
            }
            Err(e) => return Err(e.into()),
        }
    }
    if !inserted {
        return Err(AppError::Internal(
            "could not allocate a unique group code".into(),
        ));
    }

    let token = issue_token(
        &state.config.jwt_secret,
        id.clone(),
        now_secs(),
        state.config.token_ttl_secs,
    );
    Ok(Json(CreateGroupResponse {
        group_id: id,
        code,
        token,
    }))
}

/// Joins an existing group by its share code.
pub async fn join_group(
    State(state): State<AppState>,
    Json(req): Json<JoinGroupRequest>,
) -> AppResult<Json<JoinGroupResponse>> {
    let row: Option<(String,)> = sqlx::query_as("SELECT id FROM groups WHERE code = ?")
        .bind(&req.code)
        .fetch_optional(&state.db)
        .await?;
    let group_id = row.ok_or(AppError::NotFound)?.0;

    let token = issue_token(
        &state.config.jwt_secret,
        group_id.clone(),
        now_secs(),
        state.config.token_ttl_secs,
    );
    Ok(Json(JoinGroupResponse { group_id, token }))
}

/// Pulls all records with `server_seq > since`.
pub async fn get_changes(
    State(state): State<AppState>,
    GroupToken(claims): GroupToken,
    Path(group_id): Path<String>,
    Query(query): Query<ChangesQuery>,
) -> AppResult<Json<PullResponse>> {
    authorize(&claims, &group_id)?;

    let rows: Vec<RecordRow> = sqlx::query_as(
        "SELECT id, updated_at, deleted, device_id, payload, server_seq
         FROM records
         WHERE group_id = ? AND server_seq > ?
         ORDER BY server_seq",
    )
    .bind(&group_id)
    .bind(query.since)
    .fetch_all(&state.db)
    .await?;

    let mut seq = query.since;
    let records: Vec<RecordOut> = rows
        .into_iter()
        .map(|r| {
            seq = seq.max(r.server_seq);
            r.into_out()
        })
        .collect();

    Ok(Json(PullResponse { records, seq }))
}

/// Pushes a batch of records, reconciling each with last-write-wins.
pub async fn post_changes(
    State(state): State<AppState>,
    GroupToken(claims): GroupToken,
    Path(group_id): Path<String>,
    Json(req): Json<PushRequest>,
) -> AppResult<Json<PushResponse>> {
    authorize(&claims, &group_id)?;

    let mut applied = Vec::new();
    let mut rejected = Vec::new();
    let mut tx = state.db.begin().await?;

    for rec in &req.records {
        let payload = B64
            .decode(rec.payload.as_bytes())
            .map_err(|_| AppError::BadRequest("payload is not valid base64".into()))?;

        let existing: Option<(i64, String)> = sqlx::query_as(
            "SELECT updated_at, device_id FROM records WHERE group_id = ? AND id = ?",
        )
        .bind(&group_id)
        .bind(&rec.id)
        .fetch_optional(&mut *tx)
        .await?;

        // Last-write-wins: newer `updated_at` wins; ties broken by the larger `device_id`.
        let wins = match &existing {
            None => true,
            Some((cur_updated, cur_device)) => {
                rec.updated_at > *cur_updated
                    || (rec.updated_at == *cur_updated
                        && rec.device_id.as_str() > cur_device.as_str())
            }
        };
        if !wins {
            rejected.push(rec.id.clone());
            continue;
        }

        let (server_seq,): (i64,) =
            sqlx::query_as("UPDATE change_seq SET value = value + 1 WHERE id = 0 RETURNING value")
                .fetch_one(&mut *tx)
                .await?;

        sqlx::query(
            "INSERT INTO records (group_id, id, updated_at, deleted, device_id, payload, server_seq)
             VALUES (?, ?, ?, ?, ?, ?, ?)
             ON CONFLICT (group_id, id) DO UPDATE SET
                 updated_at = excluded.updated_at,
                 deleted    = excluded.deleted,
                 device_id  = excluded.device_id,
                 payload    = excluded.payload,
                 server_seq = excluded.server_seq",
        )
        .bind(&group_id)
        .bind(&rec.id)
        .bind(rec.updated_at)
        .bind(rec.deleted as i64)
        .bind(&rec.device_id)
        .bind(&payload)
        .bind(server_seq)
        .execute(&mut *tx)
        .await?;

        applied.push(rec.id.clone());
    }

    let (seq,): (i64,) =
        sqlx::query_as("SELECT COALESCE(MAX(server_seq), 0) FROM records WHERE group_id = ?")
            .bind(&group_id)
            .fetch_one(&mut *tx)
            .await?;

    tx.commit().await?;

    Ok(Json(PushResponse {
        seq,
        applied,
        rejected,
    }))
}

fn authorize(claims: &Claims, group_id: &str) -> AppResult<()> {
    if claims.gid == group_id {
        Ok(())
    } else {
        Err(AppError::Forbidden)
    }
}

fn generate_code() -> String {
    // Map CSPRNG bytes (uuid v4 is getrandom-backed) onto the code alphabet.
    let mut bytes = Vec::with_capacity(16);
    while bytes.len() < CODE_LEN {
        bytes.extend_from_slice(Uuid::new_v4().as_bytes());
    }
    bytes[..CODE_LEN]
        .iter()
        .map(|&b| CODE_ALPHABET[b as usize % CODE_ALPHABET.len()] as char)
        .collect()
}

fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}

fn now_secs() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn generated_codes_have_expected_shape() {
        let code = generate_code();
        assert_eq!(code.len(), CODE_LEN);
        assert!(code.bytes().all(|b| CODE_ALPHABET.contains(&b)));
    }
}
