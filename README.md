# Quits

Split expenses with friends — custom fractions, multiple payers, multiple currencies — that stay
in sync across devices. No account required: create a group, share a code/link, done.

- **App:** Compose Multiplatform (Android + iOS), offline-first, local Room database.
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

## License

AGPL-3.0-or-later.
