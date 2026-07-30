> [!Warning]
> **Free and Open-Source Android is under threat.**
>
> Google will turn Android into a locked-down platform, restricting your essential freedom to install apps of your choice. Make your voice heard
>
> [**Keep Android Open**](https://keepandroidopen.org/).

# <img alt="Logo" src="icon/quits-icon.svg" width="40" height="40"/> Quits

Quits is a free and open source (FOSS) expense splitting application for Android and [Web/PWA](https://app.quits.eloque.nz) (and iOS, should Apple ever change their ridiculous developer fees).
Inspired by splitwise, splid, tricount and others, this application allows expense splitting
while being fully free as in beer (and in freedom) forever, with
end-to-end encrypted synchronization for a better privacy footprint
compared to snooping, tracker-infested corporate apps.

Features (non-exhaustive):
* Split expenses via different split types: equal, exact, shares, percentage or itemized
* e2e-encrypted synchronization via a relay server (free public instance or self-hosted)
* Statistics
* CSV export
* Full offline usage

## Installation

[<img src="https://raw.githubusercontent.com/SeineEloquenz/quits/refs/heads/main/.github/badges/github.png"
alt="Get it on GitHub"
height="80">](https://github.com/SeineEloquenz/quits/releases)
<!--
[<img src="https://raw.githubusercontent.com/SeineEloquenz/quits/refs/heads/main/.github/badges/fdroid.png"
alt="Get it on F-Droid"
height="80">](https://f-droid.org/packages/nz.eloque.quits/)
[<img src="https://raw.githubusercontent.com/SeineEloquenz/quits/refs/heads/main/.github/badges/play.png"
alt="Get it on Google Play"
height="80">](https://play.google.com/store/apps/details?id=nz.eloque.quits)
-->

### Verification
To verify the authenticity of a Quits APK, use the following SHA-256 fingerprint:


`23:F2:05:A9:19:B0:8F:F7:29:BA:E4:21:51:22:47:D9:FF:0E:FC:16:8B:21:A0:1F:A4:C7:47:C1:8A:A9:8B:47`

<!--
> [!NOTE]
> The Google Play releases are signed by Google and use a different signing key.
> Prefer using builds from GitHub or F-Droid if possible.
-->

## Backend
The quits relay is a Rust server that you can either self-host directly by building a binary via cargo, or install via the provided nix module.
The web frontend is continuously built by CI onto the `web` branch.

## License

This project is licensed under the [GNU Affero General Public License v3.0](LICENSE).
