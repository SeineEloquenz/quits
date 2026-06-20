{
  config,
  lib,
  pkgs,
  ...
}:

let
  cfg = config.services.quits-server;

  # Settings exposed as plain (non-secret) environment variables. Secrets
  # (QUITS_JWT_SECRET, QUITS_INSTANCE_SECRET) must come from `environmentFile`
  # so they never land in the world-readable Nix store.
  baseEnv = {
    QUITS_ADDR = "${cfg.host}:${toString cfg.port}";
    DATABASE_URL = cfg.databaseUrl;
    QUITS_DB_MAX_CONNECTIONS = toString cfg.dbMaxConnections;
    QUITS_TOKEN_TTL_SECS = toString cfg.tokenTtlSecs;
    RUST_LOG = cfg.logLevel;
  };
in
{
  options.services.quits-server = {
    enable = lib.mkEnableOption "the Quits sync relay";

    package = lib.mkOption {
      type = lib.types.package;
      default = pkgs.callPackage ./server.nix { };
      defaultText = lib.literalExpression "pkgs.callPackage ./server.nix { }";
      description = "The quits-server package to run.";
    };

    host = lib.mkOption {
      type = lib.types.str;
      default = "127.0.0.1";
      example = "0.0.0.0";
      description = ''
        Address the relay binds to. Defaults to loopback; set to `0.0.0.0`
        (and enable {option}`openFirewall` or a reverse proxy) to serve externally.
      '';
    };

    port = lib.mkOption {
      type = lib.types.port;
      default = 8080;
      description = "TCP port the relay listens on.";
    };

    databaseUrl = lib.mkOption {
      type = lib.types.str;
      default = "sqlite:${cfg.stateDir}/quits.db";
      defaultText = lib.literalExpression ''"sqlite:''${cfg.stateDir}/quits.db"'';
      description = "SQLite connection URL (`DATABASE_URL`). The file is created if missing.";
    };

    stateDir = lib.mkOption {
      type = lib.types.path;
      default = "/var/lib/quits-server";
      description = ''
        Persistent state directory, exposed to the service as a systemd
        `StateDirectory`. Holds the SQLite database by default.
      '';
    };

    dbMaxConnections = lib.mkOption {
      type = lib.types.ints.positive;
      default = 5;
      description = "Maximum SQLite pool connections (`QUITS_DB_MAX_CONNECTIONS`).";
    };

    tokenTtlSecs = lib.mkOption {
      type = lib.types.ints.positive;
      default = 60 * 60 * 24 * 3650;
      description = "Lifetime of issued group tokens in seconds (`QUITS_TOKEN_TTL_SECS`).";
    };

    logLevel = lib.mkOption {
      type = lib.types.str;
      default = "info";
      example = "quits_server=debug,tower_http=debug";
      description = "Value for `RUST_LOG` (tracing env-filter).";
    };

    openFirewall = lib.mkOption {
      type = lib.types.bool;
      default = false;
      description = "Whether to open {option}`port` in the firewall.";
    };

    environmentFile = lib.mkOption {
      type = lib.types.nullOr lib.types.path;
      default = null;
      example = "/run/secrets/quits-server.env";
      description = ''
        Path to an environment file (read by systemd as root) holding secrets,
        kept out of the Nix store. Strongly recommended in production to set:

        - `QUITS_JWT_SECRET` — HS256 key for group tokens; without it an
          ephemeral key is generated and all tokens are invalidated on restart.
        - `QUITS_INSTANCE_SECRET` — optional; when set, locks group creation
          behind the `X-Quits-Instance` header.

        Example file contents:

        ```
        QUITS_JWT_SECRET=...
        QUITS_INSTANCE_SECRET=...
        ```
      '';
    };

    extraEnvironment = lib.mkOption {
      type = lib.types.attrsOf lib.types.str;
      default = { };
      description = "Extra environment variables for the service (overrides defaults).";
    };
  };

  config = lib.mkIf cfg.enable {
    systemd.services.quits-server = {
      description = "Quits sync relay";
      wantedBy = [ "multi-user.target" ];
      after = [ "network-online.target" ];
      wants = [ "network-online.target" ];

      environment = baseEnv // cfg.extraEnvironment;

      serviceConfig = {
        ExecStart = lib.getExe cfg.package;
        EnvironmentFile = lib.optional (cfg.environmentFile != null) cfg.environmentFile;

        DynamicUser = true;
        StateDirectory = "quits-server";
        WorkingDirectory = cfg.stateDir;

        Restart = "on-failure";
        RestartSec = 5;

        # Hardening
        AmbientCapabilities = lib.mkIf (cfg.port < 1024) [ "CAP_NET_BIND_SERVICE" ];
        CapabilityBoundingSet = lib.mkIf (cfg.port < 1024) [ "CAP_NET_BIND_SERVICE" ];
        NoNewPrivileges = true;
        ProtectSystem = "strict";
        ProtectHome = true;
        PrivateTmp = true;
        PrivateDevices = true;
        ProtectKernelTunables = true;
        ProtectKernelModules = true;
        ProtectKernelLogs = true;
        ProtectControlGroups = true;
        ProtectClock = true;
        ProtectHostname = true;
        RestrictAddressFamilies = [
          "AF_INET"
          "AF_INET6"
          "AF_UNIX"
        ];
        RestrictNamespaces = true;
        RestrictRealtime = true;
        RestrictSUIDSGID = true;
        LockPersonality = true;
        MemoryDenyWriteExecute = true;
        SystemCallArchitectures = "native";
        SystemCallFilter = [
          "@system-service"
          "~@privileged"
          "~@resources"
        ];
        UMask = "0077";
      };
    };

    networking.firewall.allowedTCPPorts = lib.mkIf cfg.openFirewall [ cfg.port ];
  };
}
