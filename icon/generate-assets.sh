#!/usr/bin/env bash
# Regenerate all rasterized icon assets from the SVG masters in this folder.
# Requires: inkscape, imagemagick (magick). On NixOS: nix shell nixpkgs#inkscape nixpkgs#imagemagick
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(cd "$here/.." && pwd)"

full="$here/quits-icon-fullbleed.svg"
round="$here/quits-icon.svg"

ios="$root/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset"
web="$root/composeApp/src/wasmJsMain/resources"

render() { # svg out w
  inkscape "$1" --export-type=png --export-filename="$2" -w "$3" -h "$3" >/dev/null 2>&1
}
opaque() { # png  (strip alpha so iOS/Apple accept it)
  magick "$1" -background "#F1E7D2" -flatten -alpha off "$1"
}

mkdir -p "$ios" "$web"

# iOS — single 1024, opaque, full bleed
render "$full" "$ios/icon-1024.png" 1024
opaque "$ios/icon-1024.png"

# Web — PWA / maskable (full bleed) + apple-touch (opaque)
render "$full" "$web/icon-512.png" 512
render "$full" "$web/icon-192.png" 192
render "$full" "$web/apple-touch-icon.png" 180
opaque "$web/apple-touch-icon.png"

# Web — favicons (squircle, keep transparent corners)
render "$round" "$web/favicon-32.png" 32
render "$round" "$web/favicon-16.png" 16

# Play Store listing icon (opaque, full bleed)
render "$full" "$here/play-store-icon-512.png" 512
opaque "$here/play-store-icon-512.png"

echo "Done. Generated iOS, web and Play Store rasters from the SVG masters."
