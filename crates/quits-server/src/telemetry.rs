//! Metrics: an OpenTelemetry meter provider rendered through a Prometheus endpoint.

use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use axum::Router;
use axum::extract::{MatchedPath, Request, State};
use axum::http::Method;
use axum::http::header::CONTENT_TYPE;
use axum::middleware::Next;
use axum::response::{IntoResponse, Response};
use axum::routing::get;
use opentelemetry::KeyValue;
use opentelemetry::metrics::{Counter, Histogram, MeterProvider as _, UpDownCounter};
use opentelemetry_sdk::metrics::{Aggregation, Instrument, SdkMeterProvider, Stream};
use prometheus::{Encoder, Registry, TEXT_FORMAT, TextEncoder};
use sqlx::SqlitePool;
use tokio::net::TcpListener;

use crate::config::Config;
use crate::state::AppState;

const METER: &str = "quits-server";

const LATENCY_BUCKETS: &[f64] = &[
    0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0,
];

const COUNT_BUCKETS: &[f64] = &[1.0, 2.0, 5.0, 10.0, 25.0, 50.0, 100.0, 250.0, 500.0];

const PAYLOAD_BUCKETS: &[f64] = &[256.0, 512.0, 1024.0, 2048.0, 4096.0, 8192.0, 16384.0];

/// Wider than request latency: a pass over a large database can take seconds.
const REAP_BUCKETS: &[f64] = &[0.01, 0.05, 0.1, 0.5, 1.0, 5.0, 10.0, 30.0, 60.0];

/// Defines a label type whose string values are the ones actually exported.
macro_rules! labels {
    ($(
        $(#[$meta:meta])*
        $name:ident { $($variant:ident = $label:literal),+ $(,)? }
    )+) => {
        $(
            $(#[$meta])*
            #[derive(Clone, Copy, Debug, PartialEq, Eq)]
            pub enum $name {
                $($variant),+
            }

            impl $name {
                const fn as_str(self) -> &'static str {
                    match self {
                        $(Self::$variant => $label),+
                    }
                }
            }
        )+
    };
}

labels! {
    /// How a group creation attempt ended.
    GroupCreate {
        Created = "ok",
        Forbidden = "forbidden",
        Capacity = "capacity",
        Duplicate = "duplicate",
    }

    /// How a group join attempt ended.
    GroupJoin {
        Joined = "ok",
        NotFound = "not_found",
    }

    /// Why a pushed record was not stored.
    ///
    /// `Stale` is an ordinary last-write-wins loser; the other two mean the client believes it
    /// synced data the relay discarded, and are what `quits-records-dropped` alerts on.
    RejectReason {
        Oversize = "oversize",
        Stale = "stale",
        GroupFull = "group_full",
    }

    /// Which reaper rule removed a group.
    ReapRule {
        Empty = "empty",
        Inactive = "inactive",
    }

    /// How a reaper pass ended.
    ReapOutcome {
        Succeeded = "ok",
        Failed = "error",
    }

    /// Request method, narrowed from the HTTP grammar's arbitrary tokens.
    Verb {
        Get = "GET",
        Post = "POST",
        Put = "PUT",
        Patch = "PATCH",
        Delete = "DELETE",
        Head = "HEAD",
        Options = "OPTIONS",
        Other = "other",
    }
}

impl From<&Method> for Verb {
    fn from(method: &Method) -> Self {
        match *method {
            Method::GET => Self::Get,
            Method::POST => Self::Post,
            Method::PUT => Self::Put,
            Method::PATCH => Self::Patch,
            Method::DELETE => Self::Delete,
            Method::HEAD => Self::Head,
            Method::OPTIONS => Self::Options,
            _ => Self::Other,
        }
    }
}

/// Aggregate storage figures, refreshed off the request path by [`spawn_sampler`].
#[derive(Clone, Copy, Default)]
pub struct Snapshot {
    pub groups: u64,
    pub records: u64,
    pub payload_bytes: u64,
    pub max_group_records: u64,
    pub pool_connections: u64,
    pub pool_idle: u64,
    pub db_bytes: u64,
}

/// Latest [`Snapshot`], shared between the sampler and the gauge callbacks.
///
/// Stays `None` until the first sample lands so the gauges are absent rather than reporting a
/// zero that reads as an empty database.
#[derive(Clone, Default)]
struct StorageStats(Arc<Mutex<Option<Snapshot>>>);

impl StorageStats {
    fn get(&self) -> Option<Snapshot> {
        self.0.lock().ok().and_then(|guard| *guard)
    }

    fn set(&self, snapshot: Snapshot) {
        if let Ok(mut guard) = self.0.lock() {
            *guard = Some(snapshot);
        }
    }
}

/// Instrument handles, cloned into every request handler via [`AppState`].
///
/// Exported Prometheus names are derived from the instrument names below: dots become
/// underscores, a recognised unit is appended as a suffix (`s` -> `_seconds`, `By` -> `_bytes`),
/// and monotonic counters additionally gain `_total`. Renaming an instrument or changing its unit
/// renames the exported series, which silently breaks the dashboards and alerts querying it.
#[derive(Clone)]
pub struct Metrics {
    /// Dropping the last clone shuts the provider down, so this is held despite being unread.
    _provider: SdkMeterProvider,
    registry: Option<Registry>,
    http_requests: Counter<u64>,
    http_duration: Histogram<f64>,
    http_in_flight: UpDownCounter<i64>,
    group_creates: Counter<u64>,
    group_joins: Counter<u64>,
    records_applied: Counter<u64>,
    records_rejected: Counter<u64>,
    push_batch_records: Histogram<u64>,
    pull_records: Histogram<u64>,
    record_payload_bytes: Histogram<u64>,
    storage: StorageStats,
    reaper_runs: Counter<u64>,
    reaper_groups_removed: Counter<u64>,
    reaper_duration: Histogram<f64>,
    reaper_last_success: Arc<AtomicU64>,
}

impl Metrics {
    /// Builds the meter provider, wiring up the Prometheus exporter only when an endpoint is
    /// configured. Without a reader the instruments are no-ops, so call sites stay unconditional.
    pub fn new(config: &Config) -> Self {
        let registry = config.metrics_addr.as_ref().map(|_| Registry::new());
        let mut builder = SdkMeterProvider::builder()
            .with_view(explicit_buckets(
                "quits.http.request.duration",
                LATENCY_BUCKETS,
            ))
            .with_view(explicit_buckets("quits.push.batch.records", COUNT_BUCKETS))
            .with_view(explicit_buckets("quits.pull.records", COUNT_BUCKETS))
            .with_view(explicit_buckets("quits.record.payload", PAYLOAD_BUCKETS))
            .with_view(explicit_buckets("quits.reaper.duration", REAP_BUCKETS));

        if let Some(registry) = &registry {
            match opentelemetry_prometheus::exporter()
                .with_registry(registry.clone())
                .without_scope_info()
                .without_target_info()
                .build()
            {
                Ok(exporter) => builder = builder.with_reader(exporter),
                Err(e) => tracing::error!("metrics exporter setup failed: {e}"),
            }
        }

        let provider = builder.build();
        let meter = provider.meter(METER);

        let version = env!("CARGO_PKG_VERSION");
        meter
            .u64_observable_gauge("quits.build.info")
            .with_description("Always 1, carrying the running version as a label.")
            .with_callback(move |observer| {
                observer.observe(1, &[KeyValue::new("version", version)]);
            })
            .build();

        let http_requests = meter
            .u64_counter("quits.http.requests")
            .with_description("HTTP requests handled, by method, route and status.")
            .build();
        let http_duration = meter
            .f64_histogram("quits.http.request.duration")
            .with_unit("s")
            .with_description("Time spent handling a request.")
            .build();
        let http_in_flight = meter
            .i64_up_down_counter("quits.http.requests.in_flight")
            .with_description("Requests currently being handled.")
            .build();

        let group_creates = meter
            .u64_counter("quits.group.creates")
            .with_description("Group creation attempts, by outcome.")
            .build();
        let group_joins = meter
            .u64_counter("quits.group.joins")
            .with_description("Group join attempts, by outcome.")
            .build();
        let records_applied = meter
            .u64_counter("quits.records.applied")
            .with_description("Records accepted into a group.")
            .build();
        let records_rejected = meter
            .u64_counter("quits.records.rejected")
            .with_description("Records a push did not store, by reason.")
            .build();
        let push_batch_records = meter
            .u64_histogram("quits.push.batch.records")
            .with_description("Records offered per push.")
            .build();
        let pull_records = meter
            .u64_histogram("quits.pull.records")
            .with_description("Records returned per pull.")
            .build();
        let record_payload_bytes = meter
            .u64_histogram("quits.record.payload")
            .with_unit("By")
            .with_description("Decoded size of a pushed record payload.")
            .build();

        let storage = StorageStats::default();
        let storage_gauge = |name: &'static str,
                             unit: &'static str,
                             description: &'static str,
                             pick: fn(&Snapshot) -> u64| {
            let stats = storage.clone();
            meter
                .u64_observable_gauge(name)
                .with_unit(unit)
                .with_description(description)
                .with_callback(move |observer| {
                    if let Some(snapshot) = stats.get() {
                        observer.observe(pick(&snapshot), &[]);
                    }
                })
                .build();
        };

        storage_gauge("quits.groups", "", "Groups stored.", |s| s.groups);
        storage_gauge("quits.records", "", "Records stored.", |s| s.records);
        storage_gauge(
            "quits.storage.payload",
            "By",
            "Total size of stored payloads.",
            |s| s.payload_bytes,
        );
        storage_gauge(
            "quits.group.records.max",
            "",
            "Records in the largest group.",
            |s| s.max_group_records,
        );
        storage_gauge(
            "quits.db.size",
            "By",
            "Size of the SQLite database file.",
            |s| s.db_bytes,
        );

        // Alerts compare usage against these rather than hardcoding a limit
        let limit_gauge = |name: &'static str, unit: &'static str, value: u64| {
            meter
                .u64_observable_gauge(name)
                .with_unit(unit)
                .with_description("Configured limit, 0 meaning unlimited.")
                .with_callback(move |observer| observer.observe(value, &[]))
                .build();
        };

        limit_gauge("quits.max.groups", "", config.max_groups);
        limit_gauge(
            "quits.max.records_per_group",
            "",
            config.max_records_per_group,
        );
        limit_gauge("quits.max.record", "By", config.max_record_bytes as u64);

        let pool_stats = storage.clone();
        meter
            .i64_observable_gauge("quits.db.connections")
            .with_description("SQLite pool connections, by state.")
            .with_callback(move |observer| {
                let Some(snapshot) = pool_stats.get() else {
                    return;
                };
                let idle = snapshot.pool_idle.min(snapshot.pool_connections);
                observer.observe(idle as i64, &[KeyValue::new("state", "idle")]);
                observer.observe(
                    (snapshot.pool_connections - idle) as i64,
                    &[KeyValue::new("state", "in_use")],
                );
            })
            .build();

        let reaper_runs = meter
            .u64_counter("quits.reaper.runs")
            .with_description("Reaper passes, by outcome.")
            .build();
        let reaper_groups_removed = meter
            .u64_counter("quits.reaper.groups.removed")
            .with_description("Groups deleted by the reaper, by rule.")
            .build();
        let reaper_duration = meter
            .f64_histogram("quits.reaper.duration")
            .with_unit("s")
            .with_description("Time taken by a reaper pass.")
            .build();

        let reaper_last_success = Arc::new(AtomicU64::new(0));
        let last_success = reaper_last_success.clone();
        meter
            .u64_observable_gauge("quits.reaper.last_success.timestamp")
            .with_unit("s")
            .with_description("When the reaper last completed a pass.")
            .with_callback(move |observer| {
                // Zero means it has never run, which must stay absent rather than reporting the
                // epoch: the staleness alert subtracts this from now().
                match last_success.load(Ordering::Relaxed) {
                    0 => {}
                    at => observer.observe(at, &[]),
                }
            })
            .build();

        Self {
            _provider: provider,
            registry,
            http_requests,
            http_duration,
            http_in_flight,
            group_creates,
            group_joins,
            records_applied,
            records_rejected,
            push_batch_records,
            pull_records,
            record_payload_bytes,
            storage,
            reaper_runs,
            reaper_groups_removed,
            reaper_duration,
            reaper_last_success,
        }
    }

    pub fn group_created(&self, outcome: GroupCreate) {
        self.group_creates
            .add(1, &[KeyValue::new("outcome", outcome.as_str())]);
    }

    pub fn group_joined(&self, outcome: GroupJoin) {
        self.group_joins
            .add(1, &[KeyValue::new("outcome", outcome.as_str())]);
    }

    pub fn record_rejected(&self, reason: RejectReason) {
        self.records_rejected
            .add(1, &[KeyValue::new("reason", reason.as_str())]);
    }

    pub fn records_applied(&self, count: usize) {
        self.records_applied.add(count as u64, &[]);
    }

    pub fn push_offered(&self, records: usize) {
        self.push_batch_records.record(records as u64, &[]);
    }

    pub fn pull_returned(&self, records: usize) {
        self.pull_records.record(records as u64, &[]);
    }

    pub fn record_payload(&self, bytes: usize) {
        self.record_payload_bytes.record(bytes as u64, &[]);
    }

    pub fn reaper_removed(&self, rule: ReapRule, groups: u64) {
        if groups > 0 {
            self.reaper_groups_removed
                .add(groups, &[KeyValue::new("rule", rule.as_str())]);
        }
    }

    pub fn reaper_finished(&self, outcome: ReapOutcome, elapsed: Duration) {
        self.reaper_runs
            .add(1, &[KeyValue::new("outcome", outcome.as_str())]);
        self.reaper_duration.record(elapsed.as_secs_f64(), &[]);
        if outcome == ReapOutcome::Succeeded {
            self.reaper_last_success
                .store(now_secs(), Ordering::Relaxed);
        }
    }

    fn render(&self) -> String {
        let Some(registry) = &self.registry else {
            return String::new();
        };
        let mut buf = Vec::new();
        match TextEncoder::new().encode(&registry.gather(), &mut buf) {
            Ok(()) => String::from_utf8(buf).unwrap_or_default(),
            Err(e) => {
                tracing::error!("metrics encoding failed: {e}");
                String::new()
            }
        }
    }
}

/// The SDK's default boundaries assume milliseconds, which puts every real observation of a
/// seconds-valued histogram in the first bucket.
fn explicit_buckets(
    instrument: &'static str,
    boundaries: &'static [f64],
) -> impl Fn(&Instrument) -> Option<Stream> + Send + Sync + 'static {
    move |candidate| {
        if candidate.name() != instrument {
            return None;
        }
        Stream::builder()
            .with_aggregation(Aggregation::ExplicitBucketHistogram {
                boundaries: boundaries.to_vec(),
                record_min_max: false,
            })
            .build()
            .ok()
    }
}

/// Route template of a matched request, carried out through the response.
#[derive(Clone)]
struct MatchedRoute(String);

/// Records the matched route on the response.
///
/// Must stay a `route_layer`: [`MatchedPath`] only exists once the router has matched, but
/// [`track_requests`] runs outside routing so that rate-limited and oversized requests are still
/// counted. This carries the label across that gap.
pub async fn tag_route(req: Request, next: Next) -> Response {
    let matched = req
        .extensions()
        .get::<MatchedPath>()
        .map(|path| path.as_str().to_owned());

    let mut res = next.run(req).await;
    if let Some(route) = matched {
        res.extensions_mut().insert(MatchedRoute(route));
    }
    res
}

/// Counts and times every request.
pub async fn track_requests(State(state): State<AppState>, req: Request, next: Next) -> Response {
    let method = Verb::from(req.method());
    let metrics = state.metrics;

    metrics.http_in_flight.add(1, &[]);
    let started = Instant::now();
    let res = next.run(req).await;
    let elapsed = started.elapsed().as_secs_f64();
    metrics.http_in_flight.add(-1, &[]);

    // Requests that never matched a route share one bucket
    let route = res
        .extensions()
        .get::<MatchedRoute>()
        .map_or("other", |matched| matched.0.as_str())
        .to_owned();

    let attributes = [
        KeyValue::new("method", method.as_str()),
        KeyValue::new("route", route),
        KeyValue::new("status", res.status().as_u16().to_string()),
    ];
    metrics.http_requests.add(1, &attributes);
    metrics.http_duration.record(elapsed, &attributes[..2]);

    res
}

async fn metrics_handler(State(metrics): State<Metrics>) -> impl IntoResponse {
    ([(CONTENT_TYPE, TEXT_FORMAT)], metrics.render())
}

/// Serves `/metrics` on its own listener.
///
/// Deliberately not merged into the main router: that port is published to the internet through
/// the reverse proxy, and this endpoint is meant to stay loopback-only.
pub async fn serve(metrics: Metrics, addr: &str) {
    let listener = match TcpListener::bind(addr).await {
        Ok(listener) => listener,
        // The relay serves sync traffic with or without metrics, so this must not be fatal.
        Err(e) => return tracing::error!("failed to bind metrics endpoint {addr}: {e}"),
    };
    tracing::info!("metrics endpoint listening on http://{addr}/metrics");

    let app = Router::new()
        .route("/metrics", get(metrics_handler))
        .with_state(metrics);

    if let Err(e) = axum::serve(listener, app).await {
        tracing::error!("metrics endpoint stopped: {e}");
    }
}

/// Reads the aggregate storage figures in one pass.
pub async fn sample_once(db: &SqlitePool) -> Result<Snapshot, sqlx::Error> {
    let (groups,): (i64,) = sqlx::query_as("SELECT COUNT(*) FROM groups")
        .fetch_one(db)
        .await?;
    let (records, payload_bytes): (i64, i64) =
        sqlx::query_as("SELECT COUNT(*), COALESCE(SUM(LENGTH(payload)), 0) FROM records")
            .fetch_one(db)
            .await?;
    let (max_group_records,): (i64,) = sqlx::query_as(
        "SELECT COALESCE(MAX(n), 0) FROM (SELECT COUNT(*) AS n FROM records GROUP BY group_id)",
    )
    .fetch_one(db)
    .await?;
    let (db_bytes,): (i64,) = sqlx::query_as(
        "SELECT (SELECT * FROM pragma_page_count()) * (SELECT * FROM pragma_page_size())",
    )
    .fetch_one(db)
    .await?;

    Ok(Snapshot {
        groups: groups as u64,
        records: records as u64,
        payload_bytes: payload_bytes as u64,
        max_group_records: max_group_records as u64,
        pool_connections: u64::from(db.size()),
        pool_idle: db.num_idle() as u64,
        db_bytes: db_bytes as u64,
    })
}

/// Refreshes the storage gauges on a timer.
pub fn spawn_sampler(db: SqlitePool, metrics: Metrics, interval_secs: u64) {
    if interval_secs == 0 {
        return;
    }
    tokio::spawn(async move {
        let mut ticker = tokio::time::interval(Duration::from_secs(interval_secs));
        loop {
            ticker.tick().await;
            match sample_once(&db).await {
                Ok(snapshot) => metrics.storage.set(snapshot),
                Err(e) => tracing::error!("storage metrics sample failed: {e}"),
            }
        }
    });
}

fn now_secs() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs()
}
