//! Metrics: an OpenTelemetry meter provider rendered through a Prometheus endpoint.

use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use axum::Router;
use axum::extract::{MatchedPath, Request, State};
use axum::http::Method;
use axum::http::header::CONTENT_TYPE;
use axum::middleware::Next;
use axum::response::{IntoResponse, Response};
use axum::routing::get;
use opentelemetry::KeyValue;
use opentelemetry::metrics::{Counter, Histogram, Meter, MeterProvider as _, UpDownCounter};
use opentelemetry_sdk::metrics::{Aggregation, Instrument, SdkMeterProvider, Stream};
use prometheus::{Encoder, Registry, TEXT_FORMAT, TextEncoder};
use sqlx::SqlitePool;
use tokio::net::TcpListener;

use crate::clock::now_secs;
use crate::config::Config;

const METER: &str = "quits-server";

/// Instrument names, shared between [`HISTOGRAMS`] and the builders that create the instruments.
/// A view whose name stops matching silently leaves the SDK's millisecond defaults in place.
const HTTP_DURATION: &str = "quits.http.request.duration";
const PUSH_BATCH: &str = "quits.push.batch.records";
const PULL_RECORDS: &str = "quits.pull.records";
const RECORD_PAYLOAD: &str = "quits.record.payload";
const REAP_DURATION: &str = "quits.reaper.duration";

const COUNT_BUCKETS: &[f64] = &[1.0, 2.0, 5.0, 10.0, 25.0, 50.0, 100.0, 250.0, 500.0];

/// Every histogram needs explicit boundaries: the SDK's defaults assume milliseconds, which puts
/// every real observation of a seconds-valued histogram in the first bucket.
const HISTOGRAMS: &[(&str, &[f64])] = &[
    (
        HTTP_DURATION,
        &[
            0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0,
        ],
    ),
    (PUSH_BATCH, COUNT_BUCKETS),
    (PULL_RECORDS, COUNT_BUCKETS),
    (
        RECORD_PAYLOAD,
        &[256.0, 512.0, 1024.0, 2048.0, 4096.0, 8192.0, 16384.0],
    ),
    // Wider than request latency: a pass over a large database can take seconds.
    (
        REAP_DURATION,
        &[0.01, 0.05, 0.1, 0.5, 1.0, 5.0, 10.0, 30.0, 60.0],
    ),
];

/// A gauge reading one figure out of the latest [`Snapshot`].
struct StorageGauge {
    name: &'static str,
    unit: &'static str,
    description: &'static str,
    read: fn(&Snapshot) -> u64,
}

const STORAGE_GAUGES: &[StorageGauge] = &[
    StorageGauge {
        name: "quits.groups",
        unit: "",
        description: "Groups stored.",
        read: |s| s.groups,
    },
    StorageGauge {
        name: "quits.records",
        unit: "",
        description: "Records stored.",
        read: |s| s.records,
    },
    StorageGauge {
        name: "quits.storage.payload",
        unit: "By",
        description: "Total size of stored payloads.",
        read: |s| s.payload_bytes,
    },
    StorageGauge {
        name: "quits.group.records.max",
        unit: "",
        description: "Records in the largest group.",
        read: |s| s.max_group_records,
    },
    StorageGauge {
        name: "quits.db.size",
        unit: "By",
        description: "Size of the SQLite database file.",
        read: |s| s.db_bytes,
    },
];

/// Defines a label type whose string values are the ones actually exported.
///
/// Those strings appear in dashboard and alert queries, so changing one silently changes which
/// series the queries match. The variants exist so a call site cannot invent a value at all.
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

/// Aggregate storage figures, refreshed off the request path by [`Metrics::spawn_sampler`].
#[derive(Clone, Copy, Default)]
struct Snapshot {
    groups: u64,
    records: u64,
    payload_bytes: u64,
    max_group_records: u64,
    pool_connections: u64,
    pool_idle: u64,
    db_bytes: u64,
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

    fn register(&self, meter: &Meter) {
        for gauge in STORAGE_GAUGES {
            let stats = self.clone();
            meter
                .u64_observable_gauge(gauge.name)
                .with_unit(gauge.unit)
                .with_description(gauge.description)
                .with_callback(move |observer| {
                    if let Some(snapshot) = stats.get() {
                        observer.observe((gauge.read)(&snapshot), &[]);
                    }
                })
                .build();
        }

        let stats = self.clone();
        meter
            .i64_observable_gauge("quits.db.connections")
            .with_description("SQLite pool connections, by state.")
            .with_callback(move |observer| {
                let Some(snapshot) = stats.get() else { return };
                let in_use = snapshot.pool_connections.saturating_sub(snapshot.pool_idle);
                observer.observe(snapshot.pool_idle as i64, &[KeyValue::new("state", "idle")]);
                observer.observe(in_use as i64, &[KeyValue::new("state", "in_use")]);
            })
            .build();
    }
}

#[derive(Clone)]
struct Http {
    requests: Counter<u64>,
    duration: Histogram<f64>,
    in_flight: UpDownCounter<i64>,
}

impl Http {
    fn new(meter: &Meter) -> Self {
        Self {
            requests: meter
                .u64_counter("quits.http.requests")
                .with_description("HTTP requests handled, by method, route and status.")
                .build(),
            duration: meter
                .f64_histogram(HTTP_DURATION)
                .with_unit("s")
                .with_description("Time spent handling a request.")
                .build(),
            in_flight: meter
                .i64_up_down_counter("quits.http.requests.in_flight")
                .with_description("Requests currently being handled.")
                .build(),
        }
    }
}

/// Instruments for the sync protocol itself: group lifecycle and record reconciliation.
#[derive(Clone)]
struct Relay {
    group_creates: Counter<u64>,
    group_joins: Counter<u64>,
    records_applied: Counter<u64>,
    records_rejected: Counter<u64>,
    push_batch: Histogram<u64>,
    pull_batch: Histogram<u64>,
    payload_bytes: Histogram<u64>,
}

impl Relay {
    fn new(meter: &Meter) -> Self {
        Self {
            group_creates: meter
                .u64_counter("quits.group.creates")
                .with_description("Group creation attempts, by outcome.")
                .build(),
            group_joins: meter
                .u64_counter("quits.group.joins")
                .with_description("Group join attempts, by outcome.")
                .build(),
            records_applied: meter
                .u64_counter("quits.records.applied")
                .with_description("Records accepted into a group.")
                .build(),
            records_rejected: meter
                .u64_counter("quits.records.rejected")
                .with_description("Records a push did not store, by reason.")
                .build(),
            push_batch: meter
                .u64_histogram(PUSH_BATCH)
                .with_description("Records offered per push.")
                .build(),
            pull_batch: meter
                .u64_histogram(PULL_RECORDS)
                .with_description("Records returned per pull.")
                .build(),
            payload_bytes: meter
                .u64_histogram(RECORD_PAYLOAD)
                .with_unit("By")
                .with_description("Decoded size of a pushed record payload.")
                .build(),
        }
    }
}

#[derive(Clone)]
struct Reaper {
    runs: Counter<u64>,
    groups_removed: Counter<u64>,
    duration: Histogram<f64>,
    last_success: Arc<AtomicU64>,
}

impl Reaper {
    fn new(meter: &Meter) -> Self {
        let last_success = Arc::new(AtomicU64::new(0));

        let observed = last_success.clone();
        meter
            .u64_observable_gauge("quits.reaper.last_success.timestamp")
            .with_unit("s")
            .with_description("When the reaper last completed a pass.")
            .with_callback(move |observer| {
                // Zero means it has never run, which must stay absent rather than reporting the
                // epoch: the staleness alert subtracts this from now().
                match observed.load(Ordering::Relaxed) {
                    0 => {}
                    at => observer.observe(at, &[]),
                }
            })
            .build();

        Self {
            runs: meter
                .u64_counter("quits.reaper.runs")
                .with_description("Reaper passes, by outcome.")
                .build(),
            groups_removed: meter
                .u64_counter("quits.reaper.groups.removed")
                .with_description("Groups deleted by the reaper, by rule.")
                .build(),
            duration: meter
                .f64_histogram(REAP_DURATION)
                .with_unit("s")
                .with_description("Time taken by a reaper pass.")
                .build(),
            last_success,
        }
    }
}

/// Instrument handles, cloned into every request handler via [`crate::state::AppState`].
///
/// Exported Prometheus names are derived from the instrument names above: dots become
/// underscores, a recognised unit is appended as a suffix (`s` -> `_seconds`, `By` -> `_bytes`),
/// and monotonic counters additionally gain `_total`. Renaming an instrument or changing its unit
/// renames the exported series, which silently breaks the dashboards and alerts querying it.
#[derive(Clone)]
pub struct Metrics {
    /// Dropping the last clone shuts the provider down, so this is held despite being unread.
    _provider: SdkMeterProvider,
    registry: Registry,
    http: Http,
    relay: Relay,
    reaper: Reaper,
    storage: StorageStats,
}

impl Metrics {
    /// Builds the meter provider, attaching the Prometheus exporter only when an endpoint is
    /// configured. Without a reader the instruments are no-ops, so call sites stay unconditional.
    pub fn new(config: &Config) -> Self {
        let registry = Registry::new();
        let provider = build_provider(config, &registry);
        let meter = provider.meter(METER);

        register_build_info(&meter);
        register_limits(&meter, config);

        let storage = StorageStats::default();
        storage.register(&meter);

        Self {
            http: Http::new(&meter),
            relay: Relay::new(&meter),
            reaper: Reaper::new(&meter),
            storage,
            registry,
            _provider: provider,
        }
    }

    pub fn group_created(&self, outcome: GroupCreate) {
        self.relay
            .group_creates
            .add(1, &[KeyValue::new("outcome", outcome.as_str())]);
    }

    pub fn group_joined(&self, outcome: GroupJoin) {
        self.relay
            .group_joins
            .add(1, &[KeyValue::new("outcome", outcome.as_str())]);
    }

    pub fn record_rejected(&self, reason: RejectReason) {
        self.relay
            .records_rejected
            .add(1, &[KeyValue::new("reason", reason.as_str())]);
    }

    pub fn records_applied(&self, count: usize) {
        self.relay.records_applied.add(count as u64, &[]);
    }

    pub fn push_offered(&self, records: usize) {
        self.relay.push_batch.record(records as u64, &[]);
    }

    pub fn pull_returned(&self, records: usize) {
        self.relay.pull_batch.record(records as u64, &[]);
    }

    pub fn record_payload(&self, bytes: usize) {
        self.relay.payload_bytes.record(bytes as u64, &[]);
    }

    pub fn reaper_removed(&self, rule: ReapRule, groups: u64) {
        if groups > 0 {
            self.reaper
                .groups_removed
                .add(groups, &[KeyValue::new("rule", rule.as_str())]);
        }
    }

    pub fn reaper_finished(&self, outcome: ReapOutcome, elapsed: Duration) {
        self.reaper
            .runs
            .add(1, &[KeyValue::new("outcome", outcome.as_str())]);
        self.reaper.duration.record(elapsed.as_secs_f64(), &[]);
        if outcome == ReapOutcome::Succeeded {
            self.reaper
                .last_success
                .store(now_secs(), Ordering::Relaxed);
        }
    }

    /// Refreshes the storage gauges on a timer.
    ///
    /// Kept off the scrape path deliberately: summing payload lengths is a full table scan, and
    /// the collector scrapes far more often than these figures meaningfully change.
    pub fn spawn_sampler(&self, db: SqlitePool, interval_secs: u64) {
        if interval_secs == 0 {
            return;
        }
        let storage = self.storage.clone();
        tokio::spawn(async move {
            let mut ticker = tokio::time::interval(Duration::from_secs(interval_secs));
            loop {
                ticker.tick().await;
                match sample_once(&db).await {
                    Ok(snapshot) => storage.set(snapshot),
                    Err(e) => tracing::error!("storage metrics sample failed: {e}"),
                }
            }
        });
    }

    /// Serves `/metrics` on its own listener.
    ///
    /// Deliberately not merged into the main router: that port is published to the internet
    /// through the reverse proxy, and this endpoint is meant to stay loopback-only.
    pub async fn serve(self, addr: &str) {
        let listener = match TcpListener::bind(addr).await {
            Ok(listener) => listener,
            // The relay serves sync traffic with or without metrics, so this must not be fatal.
            Err(e) => return tracing::error!("failed to bind metrics endpoint {addr}: {e}"),
        };
        tracing::info!("metrics endpoint listening on http://{addr}/metrics");

        let app = Router::new()
            .route("/metrics", get(render))
            .with_state(self);

        if let Err(e) = axum::serve(listener, app).await {
            tracing::error!("metrics endpoint stopped: {e}");
        }
    }
}

fn build_provider(config: &Config, registry: &Registry) -> SdkMeterProvider {
    let mut builder = HISTOGRAMS.iter().fold(
        SdkMeterProvider::builder(),
        |builder, &(instrument, boundaries)| {
            builder.with_view(explicit_buckets(instrument, boundaries))
        },
    );

    if config.metrics_addr.is_some() {
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

    builder.build()
}

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

fn register_build_info(meter: &Meter) {
    let version = env!("CARGO_PKG_VERSION");
    meter
        .u64_observable_gauge("quits.build.info")
        .with_description("Always 1, carrying the running version as a label.")
        .with_callback(move |observer| observer.observe(1, &[KeyValue::new("version", version)]))
        .build();
}

/// Alerts compare usage against these rather than hardcoding a limit.
fn register_limits(meter: &Meter, config: &Config) {
    for (name, unit, value) in [
        ("quits.max.groups", "", config.max_groups),
        (
            "quits.max.records_per_group",
            "",
            config.max_records_per_group,
        ),
        ("quits.max.record", "By", config.max_record_bytes as u64),
    ] {
        meter
            .u64_observable_gauge(name)
            .with_unit(unit)
            .with_description("Configured limit, 0 meaning unlimited.")
            .with_callback(move |observer| observer.observe(value, &[]))
            .build();
    }
}

async fn render(State(metrics): State<Metrics>) -> impl IntoResponse {
    let mut buf = Vec::new();
    let body = match TextEncoder::new().encode(&metrics.registry.gather(), &mut buf) {
        Ok(()) => String::from_utf8(buf).unwrap_or_default(),
        Err(e) => {
            tracing::error!("metrics encoding failed: {e}");
            String::new()
        }
    };
    ([(CONTENT_TYPE, TEXT_FORMAT)], body)
}

/// Carries the matched route out through the response.
///
/// Must stay a `route_layer`: [`MatchedPath`] only exists once the router has matched, but
/// [`track_requests`] runs outside routing so that rate-limited and oversized requests are still
/// counted. This bridges that gap.
pub async fn tag_route(req: Request, next: Next) -> Response {
    let matched = req.extensions().get::<MatchedPath>().cloned();

    let mut res = next.run(req).await;
    if let Some(path) = matched {
        res.extensions_mut().insert(path);
    }
    res
}

/// Counts and times every request.
pub async fn track_requests(State(metrics): State<Metrics>, req: Request, next: Next) -> Response {
    let method = Verb::from(req.method());

    metrics.http.in_flight.add(1, &[]);
    let started = Instant::now();
    let res = next.run(req).await;
    let elapsed = started.elapsed().as_secs_f64();
    metrics.http.in_flight.add(-1, &[]);

    // Requests that never matched a route share one bucket. Labelling them with the raw path
    // would let anyone open an unbounded number of series by requesting random URLs.
    let route = res
        .extensions()
        .get::<MatchedPath>()
        .map_or("other", MatchedPath::as_str)
        .to_owned();

    let attributes = [
        KeyValue::new("method", method.as_str()),
        KeyValue::new("route", route),
        KeyValue::new("status", res.status().as_u16().to_string()),
    ];
    metrics.http.requests.add(1, &attributes);
    metrics.http.duration.record(elapsed, &attributes[..2]);

    res
}

/// Reads the aggregate storage figures in one pass.
async fn sample_once(db: &SqlitePool) -> Result<Snapshot, sqlx::Error> {
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

