{
  dockerTools,
  server,
  lib,
  ...
}:

# OCI image built straight from the Nix package — no Dockerfile. Build & load with:
#   nix build .#server-image && docker load < result
dockerTools.buildLayeredImage {
  name = "quits-server";
  tag = "latest";

  contents = [ server ];

  config = {
    Cmd = [ "${lib.getExe server}" ];
    ExposedPorts = {
      "8080/tcp" = { };
    };
    Env = [
      "QUITS_ADDR=0.0.0.0:8080"
    ];
  };
}
