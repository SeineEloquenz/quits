{
  description = "Quits — expense-splitting app + sync relay";

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
          web = import ./nix/web.nix { inherit system nixpkgs; };
        }
      );

      nixosModules = {
        quits-server = ./nix/module.nix;
        quits-web = ./nix/web-module.nix;
        default = self.nixosModules.quits-server;
      };

      apps = forAllSystems (system: {
        default = {
          type = "app";
          program = "${self.packages.${system}.server}/bin/quits-server";
        };
        # `nix run .#update-web-deps` regenerates nix/web-deps.json. Exposed as an app because the
        # updateScript's output is the script file itself (no $out/bin), which `nix run` can't find.
        update-web-deps = {
          type = "app";
          program = "${self.packages.${system}.web.updateScript}";
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
