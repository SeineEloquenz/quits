//! The client version a request reports, and the minimum the relay serves.

use std::fmt;
use std::str::FromStr;

use axum::extract::{Request, State};
use axum::http::HeaderMap;
use axum::middleware::Next;
use axum::response::{IntoResponse, Response};

use crate::error::AppError;
use crate::telemetry::Metrics;

pub const VERSION_HEADER: &str = "X-Quits-Version";

/// Oldest client version the group endpoints serve. Raise it when a release stops being supported;
/// `0.0.0` turns nobody away.
const MIN_CLIENT_VERSION: ClientVersion = ClientVersion::new(0, 0, 0);

/// A dotted client version, ordered field by field. Absent fields read as `0`.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub struct ClientVersion {
    major: u32,
    minor: u32,
    patch: u32,
}

impl fmt::Display for ClientVersion {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}.{}.{}", self.major, self.minor, self.patch)
    }
}

impl ClientVersion {
    const fn new(major: u32, minor: u32, patch: u32) -> Self {
        Self {
            major,
            minor,
            patch,
        }
    }

    /// Metric label form.
    pub fn series(self) -> String {
        format!("{}.{}", self.major, self.minor)
    }
}

impl FromStr for ClientVersion {
    type Err = ();

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let mut parts = s.trim().split('.');
        Ok(ClientVersion {
            major: component(parts.next())?,
            minor: component(parts.next())?,
            patch: component(parts.next())?,
        })
    }
}

/// Leading digits of one dotted field. Absent is `0`; present but not numeric is an error, so a
/// malformed version is ignored rather than read as `0`.
fn component(raw: Option<&str>) -> Result<u32, ()> {
    let Some(raw) = raw else { return Ok(0) };
    raw.trim()
        .chars()
        .take_while(char::is_ascii_digit)
        .collect::<String>()
        .parse()
        .map_err(|_| ())
}

/// Reads the version a request reports, if it reports a usable one.
pub fn reported_version(headers: &HeaderMap) -> Option<ClientVersion> {
    headers
        .get(VERSION_HEADER)
        .and_then(|value| value.to_str().ok())
        .and_then(|value| value.parse().ok())
}

/// Whether `min` turns `reported` away.
fn refuses(min: ClientVersion, reported: Option<ClientVersion>) -> bool {
    reported.is_some_and(|version| version < min)
}

/// Counts the reported client version, then rejects anything below [`MIN_CLIENT_VERSION`] with 426.
pub async fn check_version(
    State(metrics): State<Metrics>,
    request: Request,
    next: Next,
) -> Response {
    let reported = reported_version(request.headers());

    metrics.client_request(reported);

    if refuses(MIN_CLIENT_VERSION, reported) {
        tracing::debug!(
            ?reported,
            min = %MIN_CLIENT_VERSION,
            "refused a client below the minimum version"
        );
        return AppError::ClientTooOld(MIN_CLIENT_VERSION.to_string()).into_response();
    }

    next.run(request).await
}

#[cfg(test)]
mod tests {
    use super::*;

    fn v(s: &str) -> ClientVersion {
        s.parse().expect("parses")
    }

    #[test]
    fn parses_partial_versions() {
        assert_eq!(v("1"), v("1.0.0"));
        assert_eq!(v("1.2"), v("1.2.0"));
        assert_eq!(v("0.10.1").to_string(), "0.10.1");
    }

    #[test]
    fn orders_numerically_not_lexically() {
        assert!(v("0.10.0") > v("0.9.9"));
        assert!(v("1.0.0") > v("0.99.99"));
        assert!(v("0.10.1") > v("0.10.0"));
    }

    #[test]
    fn rejects_non_numeric_versions() {
        assert!("".parse::<ClientVersion>().is_err());
        assert!("x.y.z".parse::<ClientVersion>().is_err());
        assert!("1.x".parse::<ClientVersion>().is_err());
    }

    #[test]
    fn tolerates_a_pre_release_suffix() {
        assert_eq!(v("0.11.0-rc1"), v("0.11.0"));
    }

    fn headers(version: Option<&str>) -> HeaderMap {
        let mut headers = HeaderMap::new();
        if let Some(version) = version {
            headers.insert(VERSION_HEADER, version.parse().expect("valid header"));
        }
        headers
    }

    #[test]
    fn reads_the_version_header() {
        assert_eq!(
            reported_version(&headers(Some("0.10.1"))),
            Some(v("0.10.1"))
        );
        assert_eq!(reported_version(&headers(Some("0.11"))), Some(v("0.11.0")));
    }

    #[test]
    fn ignores_an_absent_or_unusable_header() {
        assert_eq!(reported_version(&headers(None)), None);
        assert_eq!(reported_version(&headers(Some(""))), None);
        assert_eq!(reported_version(&headers(Some("nightly"))), None);
    }

    #[test]
    fn a_minimum_refuses_only_older_reported_versions() {
        let min = v("0.10.0");
        assert!(refuses(min, Some(v("0.9.9"))));
        assert!(!refuses(min, Some(v("0.10.0"))));
        assert!(!refuses(min, Some(v("0.11.0"))));
    }

    #[test]
    fn a_minimum_never_refuses_a_caller_that_reports_nothing() {
        assert!(!refuses(v("99.0.0"), None));
    }
}
