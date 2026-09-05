//! HTTP handlers. The relay is domain-agnostic: it authorizes by group token, stores opaque
//! record payloads, and reconciles them with last-write-wins. It never parses a payload.

use std::convert::Infallible;

use axum::Json;
use axum::extract::{FromRequestParts, Path, Query, State};
use axum::http::request::Parts;
use base64::Engine as _;
use base64::engine::general_purpose::STANDARD as B64;
use serde::{Deserialize, Serialize};
use uuid::Uuid;

use crate::auth::{Claims, GroupToken, issue_token};
use crate::clock::{now_ms, now_secs};
use crate::error::{AppError, AppResult};
use crate::state::AppState;
use crate::telemetry::{GroupCreate, GroupJoin, RejectReason};

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
    pub token: String,
}

/// Opaque, client-derived handle the relay stores to find a group; used by both create and join.
/// The relay never learns the group secret it's derived from.
#[derive(Debug, Deserialize)]
pub struct GroupLookupRequest {
    pub lookup_id: String,
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

#[derive(Debug, Serialize)]
pub struct LimitsResponse {
    pub max_body_bytes: u64,
    pub max_record_bytes: u64,
    pub max_records_per_group: u64,
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

/// The instance's request and storage limits, so a client can size its pushes to fit.
pub async fn limits(State(state): State<AppState>) -> Json<LimitsResponse> {
    Json(LimitsResponse {
        max_body_bytes: state.config.max_body_bytes as u64,
        max_record_bytes: state.config.max_record_bytes as u64,
        max_records_per_group: state.config.max_records_per_group,
    })
}

/// Creates a group under a client-supplied lookup id. Gated by the optional instance secret.
pub async fn create_group(
    State(state): State<AppState>,
    ctx: ClientContext,
    Json(req): Json<GroupLookupRequest>,
) -> AppResult<Json<CreateGroupResponse>> {
    if let Some(secret) = &state.config.instance_secret
        && ctx.instance_header.as_deref() != Some(secret.as_str())
    {
        state.metrics.group_created(GroupCreate::Forbidden);
        return Err(AppError::Forbidden);
    }

    // Global backstop against unbounded creation on a public instance. Soft (racy) by design.
    if state.config.max_groups > 0 {
        let (count,): (i64,) = sqlx::query_as("SELECT COUNT(*) FROM groups")
            .fetch_one(&state.db)
            .await?;
        if count as u64 >= state.config.max_groups {
            tracing::warn!(
                count,
                limit = state.config.max_groups,
                "group creation rejected: at global group cap"
            );
            state.metrics.group_created(GroupCreate::Capacity);
            return Err(AppError::InstanceFull);
        }
    }

    let id = Uuid::new_v4().to_string();
    let created_at = now_ms();

    match sqlx::query("INSERT INTO groups (id, lookup_id, created_at) VALUES (?, ?, ?)")
        .bind(&id)
        .bind(&req.lookup_id)
        .bind(created_at)
        .execute(&state.db)
        .await
    {
        Ok(_) => {}
        Err(sqlx::Error::Database(e)) if e.is_unique_violation() => {
            state.metrics.group_created(GroupCreate::Duplicate);
            return Err(AppError::BadRequest("group already exists".into()));
        }
        Err(e) => return Err(e.into()),
    }

    let token = issue_token(
        &state.config.jwt_secret,
        id.clone(),
        now_secs(),
        state.config.token_ttl_secs,
    );
    state.metrics.group_created(GroupCreate::Created);
    Ok(Json(CreateGroupResponse {
        group_id: id,
        token,
    }))
}

/// Joins an existing group by its lookup id.
pub async fn join_group(
    State(state): State<AppState>,
    Json(req): Json<GroupLookupRequest>,
) -> AppResult<Json<JoinGroupResponse>> {
    let row: Option<(String,)> = sqlx::query_as("SELECT id FROM groups WHERE lookup_id = ?")
        .bind(&req.lookup_id)
        .fetch_optional(&state.db)
        .await?;
    let Some((group_id,)) = row else {
        state.metrics.group_joined(GroupJoin::NotFound);
        return Err(AppError::NotFound);
    };

    let token = issue_token(
        &state.config.jwt_secret,
        group_id.clone(),
        now_secs(),
        state.config.token_ttl_secs,
    );
    state.metrics.group_joined(GroupJoin::Joined);
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

    state.metrics.pull_returned(records.len());
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

    state.metrics.push_offered(req.records.len());

    let mut tx = state.db.begin().await?;

    // Everything that can refuse the push is checked before the first write, so a non-2xx always
    // means nothing was stored. Returning here drops the transaction, rolling it back.
    let payloads = decode_payloads(&state, &group_id, &req)?;
    let existing = current_rows(&mut tx, &group_id, &req).await?;
    enforce_record_cap(&state, &group_id, &mut tx, &existing).await?;

    let mut applied = Vec::new();
    let mut rejected = Vec::new();

    for ((rec, payload), existing) in req.records.iter().zip(&payloads).zip(&existing) {
        // Last-write-wins: newer `updated_at` wins; ties broken by the larger `device_id`.
        let wins = match existing {
            None => true,
            Some((cur_updated, cur_device)) => {
                rec.updated_at > *cur_updated
                    || (rec.updated_at == *cur_updated
                        && rec.device_id.as_str() > cur_device.as_str())
            }
        };
        if !wins {
            state.metrics.records_rejected(RejectReason::Stale, 1);
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
        .bind(payload.as_slice())
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

    state.metrics.records_applied(applied.len());

    Ok(Json(PushResponse {
        seq,
        applied,
        rejected,
    }))
}

/// Decodes every payload once, so the apply loop can reuse them instead of decoding again.
///
/// A single oversized record fails the whole push
fn decode_payloads(state: &AppState, group_id: &str, req: &PushRequest) -> AppResult<Vec<Vec<u8>>> {
    let limit = state.config.max_record_bytes;
    let mut payloads = Vec::with_capacity(req.records.len());
    let mut oversized = Vec::new();

    for rec in &req.records {
        let payload = B64
            .decode(rec.payload.as_bytes())
            .map_err(|_| AppError::BadRequest("payload is not valid base64".into()))?;

        state.metrics.record_payload(payload.len());
        if limit > 0 && payload.len() > limit {
            oversized.push(rec.id.clone());
        }
        payloads.push(payload);
    }

    if !oversized.is_empty() {
        tracing::warn!(
            %group_id,
            records = oversized.len(),
            limit,
            "push refused: record payload exceeds max size"
        );
        state
            .metrics
            .records_rejected(RejectReason::Oversize, oversized.len());
        return Err(AppError::RecordTooLarge(oversized));
    }

    Ok(payloads)
}

/// The stored row for each pushed id, in request order.
///
/// Feeds both the cap arithmetic and the last-write-wins comparison, so it is read once.
async fn current_rows(
    tx: &mut sqlx::SqliteConnection,
    group_id: &str,
    req: &PushRequest,
) -> Result<Vec<Option<(i64, String)>>, sqlx::Error> {
    let mut rows = Vec::with_capacity(req.records.len());
    for rec in &req.records {
        rows.push(
            sqlx::query_as(
                "SELECT updated_at, device_id FROM records WHERE group_id = ? AND id = ?",
            )
            .bind(group_id)
            .bind(&rec.id)
            .fetch_optional(&mut *tx)
            .await?,
        );
    }
    Ok(rows)
}

/// Refuses a push that would take the group past its record ceiling.
///
/// Only ids the group does not already hold count against the cap, so a batch of pure updates
/// still applies to a full group — editing an existing expense must not require deleting one.
async fn enforce_record_cap(
    state: &AppState,
    group_id: &str,
    tx: &mut sqlx::SqliteConnection,
    existing: &[Option<(i64, String)>],
) -> AppResult<()> {
    let limit = state.config.max_records_per_group;
    if limit == 0 {
        return Ok(());
    }

    let (stored,): (i64,) = sqlx::query_as("SELECT COUNT(*) FROM records WHERE group_id = ?")
        .bind(group_id)
        .fetch_one(&mut *tx)
        .await?;

    let incoming = existing.iter().filter(|row| row.is_none()).count();
    if stored as u64 + incoming as u64 <= limit {
        return Ok(());
    }

    tracing::warn!(%group_id, stored, incoming, limit, "push refused: group at record cap");
    state
        .metrics
        .records_rejected(RejectReason::GroupFull, incoming);
    Err(AppError::GroupFull)
}

fn authorize(claims: &Claims, group_id: &str) -> AppResult<()> {
    if claims.gid == group_id {
        Ok(())
    } else {
        Err(AppError::Forbidden)
    }
}
