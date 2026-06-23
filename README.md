# Quits

Split expenses with friends — custom fractions, multiple payers, multiple currencies — that stay
in sync across devices. No account required: create a group, share a code/link, done.

- **App:** Compose Multiplatform (Android, iOS, and web via Kotlin/Wasm), offline-first, local
  Room database (OPFS on the web).
- **Backend:** Rust/Axum sync relay. It stores **opaque records** per group
  and relays deltas; it has **no domain logic** and cannot read your data — groundwork for
  end-to-end encryption. All money/split/balance/FX logic lives in the app.

This is a monorepo: a Rust workspace and a Gradle build share the root.

```
quits/
├── flake.nix / shell.nix     # Nix devshell (toolchain) + Nix-built server package & OCI image
├── Cargo.toml, crates/       # Rust workspace — the sync relay (crates/quits-server)
├── settings.gradle.kts …     # Compose Multiplatform app
└── composeApp/, iosApp/      # shared app + iOS Xcode shell
```

## Development

Nix provides the whole toolchain (JDK 21, Android SDK, Gradle, Rust, sqlx-cli). With `direnv`:

```bash
direnv allow      # or: nix develop
```

### Backend

```bash
nix build .#server                 # hermetic build of the relay
nix run .                          # run it (serves /health on :8080)
# inside the devshell:
cargo run -p quits-server
cargo test
nix build .#server-image           # OCI image: docker load < result
```

#### Deploying on NixOS

The flake exports `nixosModules.quits-server`..

```nix
{
  inputs.quits.url = "github:SeineEloquenz/quits";

  outputs = { nixpkgs, quits, ... }: {
    nixosConfigurations.myhost = nixpkgs.lib.nixosSystem {
      modules = [
        quits.nixosModules.quits-server
        {
          services.quits-server = {
            enable = true;
            host = "0.0.0.0";        # default 127.0.0.1
            port = 8080;
            openFirewall = true;
            # Keep secrets out of the Nix store (managed by sops/agenix/etc).
            # Set at least QUITS_JWT_SECRET so tokens survive restarts.
            environmentFile = "/run/secrets/quits-server.env";
          };
        }
      ];
    };
  };
}
```

### Web

The app also builds for the browser via Kotlin/Wasm (Compose for Web), reusing the shared UI and
sync logic. Room persists locally through a Web Worker SQLite driver backed by the Origin Private
File System (OPFS).

```bash
# inside the devshell (provides Node + Yarn, and points Kotlin at the Nix node):
./gradlew :composeApp:wasmJsBrowserDistribution   # -> composeApp/build/dist/wasmJs/productionExecutable
./gradlew :composeApp:wasmJsBrowserDevelopmentRun # serve with hot reload
nix build .#web                                   # hermetic bundle (see note below)
```

`nix build .#web` is a pure build: a single mitm-cache fixed-output derivation captures the Maven
and npm dependencies under `nix/web-deps.json`. Regenerate that lock — without hand-editing a hash —
with the package's `updateScript` (the `update-web-deps` workflow runs it on a schedule). The build
also requires `compose-kit` to publish a `wasmJs` target.

#### Deploying the web app on NixOS

The flake exports `nixosModules.quits-web`, an nginx vhost serving the static bundle with the
cross-origin-isolation headers OPFS needs. The web client talks to the relay directly (the relay
sends permissive CORS), so the web host and relay host are independent.

```nix
{
  inputs.quits.url = "github:SeineEloquenz/quits";

  outputs = { nixpkgs, quits, ... }: {
    nixosConfigurations.myhost = nixpkgs.lib.nixosSystem {
      system = "x86_64-linux";
      modules = [
        quits.nixosModules.quits-web
        {
          services.quits-web = {
            enable = true;
            serverName = "app.quits.eloque.nz";
            package = quits.packages.x86_64-linux.web;
          };
        }
      ];
    };
  };
}
```

## License

AGPL-3.0-or-later.
