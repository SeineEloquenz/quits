{
  lib,
  rustPlatform,
  pkg-config,
  ...
}:

let
  fs = lib.fileset;
  root = ../.;
in
rustPlatform.buildRustPackage {
  pname = "quits-server";
  version = "0.1.0";

  # Only the Rust workspace participates in this build, so changes to the Gradle/Kotlin parts of
  # the monorepo don't trigger a rebuild of the backend.
  src = fs.toSource {
    inherit root;
    fileset = fs.unions [
      (root + "/Cargo.toml")
      (root + "/Cargo.lock")
      (root + "/crates")
    ];
  };

  cargoLock.lockFile = ../Cargo.lock;

  nativeBuildInputs = [ pkg-config ];

  RUST_SRC_PATH = "${rustPlatform.rustLibSrc}";

  meta = {
    description = "Quits dumb sync relay";
    mainProgram = "quits-server";
    license = lib.licenses.agpl3Plus;
  };
}
