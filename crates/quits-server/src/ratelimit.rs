//! Per-IP request rate limiting, keyed on the real client IP.
//!
//! Two limiters are built from config: a generous global one applied to every route, and a strict
//! one applied only to group creation (see [`crate::router`]). Both share the same key extractor.

use std::net::{IpAddr, Ipv4Addr, SocketAddr};
use std::sync::Arc;
use std::time::Duration;

use axum::extract::ConnectInfo;
use axum::http::Request;
use governor::middleware::StateInformationMiddleware;
use tower_governor::GovernorError;
use tower_governor::governor::{GovernorConfig, GovernorConfigBuilder};
use tower_governor::key_extractor::KeyExtractor;

use crate::config::Config;

/// Concrete config type once our key extractor is plugged in
pub type IpGovernorConfig = GovernorConfig<ClientIpExtractor, StateInformationMiddleware>;

/// Requests whose IP can't be determined all share this bucket rather than bypassing limits.
const FALLBACK_IP: IpAddr = IpAddr::V4(Ipv4Addr::UNSPECIFIED);

/// Extracts the rate-limit key (client IP). Behind a proxy it trusts a single configured header;
/// otherwise it uses the TCP peer IP from `ConnectInfo`. Never fails — unknown IPs fall back to a
/// shared bucket, so a missing header/ConnectInfo can't be used to dodge limiting.
#[derive(Clone)]
pub struct ClientIpExtractor {
    behind_proxy: bool,
    header: Arc<str>,
}

impl KeyExtractor for ClientIpExtractor {
    type Key = IpAddr;

    fn name(&self) -> &'static str {
        "client-ip"
    }

    fn extract<T>(&self, req: &Request<T>) -> Result<Self::Key, GovernorError> {
        if self.behind_proxy {
            let ip = req
                .headers()
                .get(&*self.header)
                .and_then(|v| v.to_str().ok())
                .and_then(|s| s.trim().parse::<IpAddr>().ok())
                .unwrap_or(FALLBACK_IP);
            return Ok(ip);
        }
        let ip = req
            .extensions()
            .get::<ConnectInfo<SocketAddr>>()
            .map(|ConnectInfo(addr)| addr.ip())
            .unwrap_or(FALLBACK_IP);
        Ok(ip)
    }
}

fn extractor(config: &Config) -> ClientIpExtractor {
    ClientIpExtractor {
        behind_proxy: config.behind_proxy,
        header: Arc::from(config.trusted_ip_header.as_str()),
    }
}

/// Generous limiter for every endpoint. `None` when disabled (`rate_burst == 0`).
pub fn global_config(config: &Config) -> Option<Arc<IpGovernorConfig>> {
    if config.rate_burst == 0 {
        return None;
    }
    GovernorConfigBuilder::default()
        .period(Duration::from_millis(config.rate_replenish_ms.max(1)))
        .burst_size(config.rate_burst)
        .key_extractor(extractor(config))
        .use_headers()
        .finish()
        .map(Arc::new)
}

/// Strict limiter for group creation only. `None` when disabled (`create_burst == 0`).
pub fn create_config(config: &Config) -> Option<Arc<IpGovernorConfig>> {
    if config.create_burst == 0 {
        return None;
    }
    GovernorConfigBuilder::default()
        .period(Duration::from_secs(config.create_replenish_secs.max(1)))
        .burst_size(config.create_burst)
        .key_extractor(extractor(config))
        .use_headers()
        .finish()
        .map(Arc::new)
}

/// Periodically evicts idle per-IP buckets so the limiter's memory stays bounded.
pub fn spawn_cleanup(config: Arc<IpGovernorConfig>) {
    let limiter = config.limiter().clone();
    std::thread::spawn(move || {
        loop {
            std::thread::sleep(Duration::from_secs(60));
            limiter.retain_recent();
        }
    });
}
