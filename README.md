# Quits

Split expenses with friends — custom fractions, multiple payers, multiple currencies — that stay
in sync across devices. No account required: create a group, share a code/link, done.

- **App:** Compose Multiplatform (Android + iOS), offline-first, local Room database.
- **Backend:** a deliberately *dumb* Rust/Axum sync relay. It stores **opaque records** per group
  and relays deltas; it has **no domain logic** and cannot read your data — groundwork for
  end-to-end encryption. All money/split/balance/FX logic lives in the app.

This is a monorepo: a Rust workspace and a Gradle build share the root.

```
quits/
├── flake.nix / shell.nix     # Nix devshell (toolchain) + Nix-built server package & OCI image
├── Cargo.toml, crates/       # Rust workspace — the sync relay (crates/quits-server)
├── settings.gradle.kts …     # Compose Multiplatform app (added in a later phase)
└── composeApp/, iosApp/      # shared app + iOS Xcode shell (added in a later phase)
```
The reusable UI library lives in the sibling repo `../compose-kit` (pulled in via `includeBuild`).

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

## Status

Phase 1 (scaffold) — Nix flake, Cargo workspace, `/health`, CI. See the implementation plan for
the roadmap; each phase ends in a buildable, verifiable state.

## License

AGPL-3.0-or-later.
