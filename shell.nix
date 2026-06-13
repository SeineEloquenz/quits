{
  system,
  nixpkgs,
  rust-overlay,
}:

let
  # Android SDK versions are kept in sync by the `update-android-sdk` workflow from
  # github:SeineEloquenz/ci (it scans this file).
  buildToolsVersion = "37.0.0";
  buildToolsVersions = [ buildToolsVersion ];
  platformVersions = [ "37" ];

  pkgs = import nixpkgs {
    inherit system;

    overlays = [ rust-overlay.overlays.default ];

    config.allowUnfree = true;
    config.android_sdk.accept_license = true;
  };

  jdk = pkgs.jdk21;

  androidSdk = pkgs.androidenv.composeAndroidPackages {
    inherit buildToolsVersions platformVersions;
    includeNDK = false;
    includeEmulator = false;
  };

  rustToolchain = pkgs.rust-bin.stable.latest.default.override {
    extensions = [ "rust-src" ];
  };
in
{
  default = pkgs.mkShell {
    packages = [
      # Backend (Rust/Axum sync relay)
      rustToolchain
      pkgs.sqlx-cli
      pkgs.pkg-config

      # App (Compose Multiplatform: Android target + shared code)
      jdk
      pkgs.gradle
      androidSdk.androidsdk
    ];

    env = {
      ANDROID_HOME = "${androidSdk.androidsdk}/libexec/android-sdk";
      JAVA_HOME = "${jdk}";

      # aapt2 bundled in the AGP Maven artifact is a generic-Linux binary that NixOS cannot run.
      # Override it with the Nix-patched copy.
      GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidSdk.androidsdk}/libexec/android-sdk/build-tools/${buildToolsVersion}/aapt2";

      # SQLite lives in a local file; sqlx builds offline against the committed `.sqlx` cache.
      DATABASE_URL = "sqlite:quits.db";
      LOCAL_COMPOSE_KIT = "../compose-kit";
    };

    shellHook = ''
      # Point Gradle at the Nix-provided Android SDK (only meaningful once the Gradle build exists).
      cat > "$PWD/local.properties" <<EOF
      sdk.dir=${androidSdk.androidsdk}/libexec/android-sdk
      EOF
    '';
  };
}
