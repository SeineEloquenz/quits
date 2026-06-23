{
  config,
  lib,
  ...
}:

let
  cfg = config.services.quits-web;
in
{
  options.services.quits-web = {
    enable = lib.mkEnableOption "the Quits web app (static Kotlin/Wasm bundle served by nginx)";

    package = lib.mkOption {
      type = lib.types.package;
      example = lib.literalExpression "quits.packages.\${system}.web";
      description = "The built browser distribution (productionExecutable) to serve, e.g. the flake's `web` package.";
    };

    serverName = lib.mkOption {
      type = lib.types.str;
      example = "app.quits.eloque.nz";
      description = "Virtual host name for the web app.";
    };

    enableACME = lib.mkOption {
      type = lib.types.bool;
      default = true;
      description = "Whether to obtain a TLS certificate via ACME and force HTTPS.";
    };
  };

  config = lib.mkIf cfg.enable {
    services.nginx = {
      enable = true;
      # `.wasm` -> application/wasm and other modern types.
      recommendedGzipSettings = lib.mkDefault true;
      recommendedOptimisation = lib.mkDefault true;
      recommendedTlsSettings = lib.mkDefault true;

      virtualHosts.${cfg.serverName} = {
        forceSSL = cfg.enableACME;
        enableACME = cfg.enableACME;
        root = "${cfg.package}";

        locations."/" = {
          tryFiles = "$uri $uri/ /index.html";

          extraConfig = ''
            add_header Cross-Origin-Opener-Policy "same-origin" always;
            add_header Cross-Origin-Embedder-Policy "require-corp" always;
            add_header Cross-Origin-Resource-Policy "same-origin" always;
          '';
        };

        locations."= /index.html".extraConfig = ''
          add_header Cache-Control "no-cache";
        '';

        locations."~* \\.(js|wasm|mjs)$".extraConfig = ''
          add_header Cache-Control "public, max-age=31536000, immutable";
        '';
      };
    };
  };
}
