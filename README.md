<div align="center">

<img src="front/public/logo.svg" alt="" width="160">

# HomeVentory

**Know what's in every box without opening it.**

Self-hosted home inventory. Stick a QR code on a box, scan it, see what's inside.

[![License: GPL v3](https://img.shields.io/badge/license-GPLv3-3f6596.svg)](LICENSE.md)
![Android | Web](https://img.shields.io/badge/platform-Android%20%7C%20Web-3f6596.svg)
![Self-hosted](https://img.shields.io/badge/self--hosted-SQLite-c88a52.svg)

<img src="docs/screenshots/box.png" alt="Contents of a box" width="280">
<img src="docs/screenshots/search.png" alt="Search showing where each item is" width="280">

</div>

## Features

- **Nested boxes**: boxes hold items and other boxes, as deep as you like.
- **Search**: find any item and see its full location, e.g. `Basement / Shelf 1 / Brave Fox`.
- **QR stickers**: print sticker sheets; scanning a new sticker creates its box.
- **Move things**: cut a box and paste it elsewhere; its contents move with it.
- **Android app and PWA**: `davidhomeventory://` QR links open the app directly.
- **Lightweight**: one Node.js process and a SQLite file. No accounts, no cloud.

<div align="center">
<img src="docs/screenshots/stickers.png" alt="Printable sheet of QR stickers" width="600">
</div>

## Quick start

Requires Docker with `docker-compose`.

```sh
git clone https://github.com/fxdave/HomeVentory.git
cd HomeVentory
make install    # generates back/.env, installs, migrates, builds
make prod-start # http://localhost:3001
```

The port is set in `compose.prod.override.yml` (gitignored, created by `make install` from `compose.prod.override.yml.sample`).

Open the app, enter `http://<server>:3001/api` and a password. **The first password entered becomes the server password.**

Update with `make prod-update`, stop with `make prod-stop`.

### Android app

Get the APK from [Releases](https://github.com/fxdave/HomeVentory/releases). On first start it asks for your server's address (e.g. `http://192.168.0.10:3001`); after that it loads its screens from your server, so it always matches the server's version and updates with it. Only the thin native shell (QR scanner, printing, `davidhomeventory://` links) lives in the APK.

To build it yourself (requires the Android SDK):

```sh
make build-front-apk     # APK in front/android/app/build/outputs/apk/
make install-front-apk   # or install on a connected device
```

Release builds are reproducible; see [docs/FDROID.md](docs/FDROID.md).

## Development

```sh
make start      # frontend :3000 (Vite), backend :3001 (nodemon)
```

React, Vite, Panda CSS, Capacitor · Express, [Cuple](https://github.com/fxdave/cuple), Prisma, SQLite.

## License

[GPLv3](LICENSE.md)

<a href="https://endsoftwarepatents.org/innovating-without-patents"><img style="height: 45px;" src="https://static.fsf.org/nosvn/esp/logos/patent-free.svg" alt="Patent free"></a>
