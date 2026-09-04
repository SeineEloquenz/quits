{ pkgs, module }:

let
  metricsPort = 9109;
  syncPort = 8080;
in
pkgs.testers.runNixOSTest {
  name = "quits-otel";

  nodes.machine =
    { config, ... }:
    {
      imports = [ module ];

      services.quits-server = {
        enable = true;
        port = syncPort;
        metrics.enable = true;
      };

      # Nothing but a scrape and a debug exporter: the point is to prove the endpoint is
      # reachable and parseable by a real collector, not to rebuild a telemetry pipeline.
      services.opentelemetry-collector = {
        enable = true;
        package = pkgs.opentelemetry-collector-contrib;
        settings = {
          receivers.prometheus.config.scrape_configs = [
            {
              job_name = "quits";
              scrape_interval = "5s";
              static_configs = [ { targets = [ "127.0.0.1:${toString metricsPort}" ]; } ];
            }
          ];
          exporters.debug.verbosity = "detailed";
          service.pipelines.metrics = {
            receivers = [ "prometheus" ];
            exporters = [ "debug" ];
          };
        };
      };

      environment.systemPackages = [
        pkgs.curl
        pkgs.jq
        config.services.quits-server.package
      ];
    };

  testScript = ''
    machine.wait_for_unit("quits-server.service")
    machine.wait_for_open_port(${toString metricsPort})

    metrics = machine.succeed("curl -sf localhost:${toString metricsPort}/metrics")
    assert "quits_build_info" in metrics, metrics

    # The sync port is the one published through a reverse proxy, so it must not serve metrics.
    machine.succeed("test 404 = \"$(curl -s -o /dev/null -w %{http_code} localhost:${toString syncPort}/metrics)\"")

    machine.wait_for_unit("opentelemetry-collector.service")
    machine.wait_until_succeeds(
        "journalctl -u opentelemetry-collector.service | grep -q quits_build_info"
    )

    # Without the journald layer every line would arrive as PRIORITY 6, and the gateway's
    # severity mapping would report errors as INFO. Startup warns about the ephemeral JWT
    # secret, so a level other than info is expected here.
    priorities = machine.succeed(
        "journalctl -u quits-server.service -o json | jq -r .PRIORITY | sort -u"
    ).split()
    assert "4" in priorities, priorities
    assert "6" in priorities, priorities

    # Off systemd there is no journal to write to, so the plain formatter has to still work.
    fallback = machine.succeed(
        "cd /tmp && env -u JOURNAL_STREAM QUITS_ADDR=127.0.0.1:18099 "
        "DATABASE_URL=sqlite:/tmp/fallback.db "
        "timeout 3 quits-server 2>&1 | head -20 || true"
    )
    assert "listening" in fallback, fallback
  '';
}
