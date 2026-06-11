{
  lib,
  rustPlatform,
  ...
}:

let
  fs = lib.fileset;
  root = ../.;
in
rustPlatform.buildRustPackage {
  pname = "quits-server";
  version = "0.1.0";

  src = fs.toSource {
    inherit root;
    fileset = fs.unions [
      (root + "/Cargo.toml")
      (root + "/Cargo.lock")
      (root + "/crates")
    ];
  };

  cargoLock.lockFile = ../Cargo.lock;

  meta = {
    description = "Quits sync relay";
    mainProgram = "quits-server";
    license = lib.licenses.agpl3Plus;
  };
}
