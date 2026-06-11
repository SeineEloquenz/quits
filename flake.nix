{
  description = "Quits — expense-splitting app (Compose Multiplatform) + dumb sync relay (Rust/Axum)";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    rust-overlay = {
      url = "github:oxalica/rust-overlay";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    {
      self,
      nixpkgs,
      rust-overlay,
    }:
    let
      systems = [
        "x86_64-linux"
        "aarch64-linux"
      ];

      forAllSystems =
        f:
        builtins.listToAttrs (
          map (system: {
            name = system;
            value = f system;
          }) systems
        );
    in
    {
      packages = forAllSystems (
        system:
        let
          pkgs = import nixpkgs { inherit system; };
          server = pkgs.callPackage ./nix/server.nix { };
        in
        {
          inherit server;
          default = server;
          server-image = pkgs.callPackage ./nix/image.nix { inherit server; };
        }
      );

      apps = forAllSystems (system: {
        default = {
          type = "app";
          program = "${self.packages.${system}.server}/bin/quits-server";
        };
      });

      devShells = forAllSystems (
        system:
        import ./shell.nix {
          inherit system nixpkgs rust-overlay;
        }
      );

      formatter = forAllSystems (system: (import nixpkgs { inherit system; }).nixfmt-rfc-style);
    };
}
