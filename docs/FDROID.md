# Releasing on F-Droid (reproducible build)

F-Droid builds the APK from source itself. Because the build is reproducible,
F-Droid then checks that its own build matches the APK you published, and if it
does, it ships **your** signed APK. Users can then switch between your GitHub
APK and F-Droid's without reinstalling.

Store texts and images live in `fastlane/metadata/android/en-US/`; F-Droid reads
them from the repo.

## Toolchain (must match F-Droid)

| Tool        | Version                     |
|-------------|-----------------------------|
| Node.js     | 24.22.0 (`front/.nvmrc`)    |
| JDK         | 21                          |
| Android SDK | platform 36, build-tools 36 |
| Gradle      | 8.14.3 (wrapper, checksum-pinned) |

Dependencies are pinned by `front/package-lock.json` (`npm ci`) and the Gradle
wrapper. If you bump Node, update `.nvmrc` **and** the F-Droid metadata.

## One-time setup

1. Create a release key and **back it up**. If you lose it, you can never ship
   an update to existing installs.

   ```sh
   keytool -genkeypair -keystore ~/homeventory-release.jks -alias homeventory \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Create `front/android/keystore.properties` (gitignored):

   ```properties
   storeFile=/home/you/homeventory-release.jks
   storePassword=...
   keyAlias=homeventory
   keyPassword=...
   ```

3. Get the certificate fingerprint for `AllowedAPKSigningKeys`:

   ```sh
   keytool -list -v -keystore ~/homeventory-release.jks -alias homeventory \
     | grep SHA256: | sed 's/.*SHA256: //; s/://g' | tr A-F a-f
   ```

## Every release

1. Bump `versionCode` (+1) and `versionName` in `front/android/app/build.gradle`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
3. Commit, then tag: `git tag v<versionName> && git push --tags`.
4. Build from a **clean checkout of the tag** (no stray files, fresh `npm ci`):

   ```sh
   make build-front-apk-release
   ```

5. Upload `front/android/app/build/outputs/apk/release/app-release.apk` to the
   GitHub release for the tag, named exactly `HomeVentory-<versionName>.apk`
   (this is the `Binaries:` URL F-Droid downloads).

After the first submission, F-Droid's update checker picks up new `v*` tags on
its own (`UpdateCheckMode: Tags`).

## How the app updates

The APK is a thin shell: after the user enters the server address it loads the
UI from the HomeVentory server (like the Home Assistant and Jellyfin apps), and
the server's PWA service worker keeps it current. So most changes ship by
updating the server; a new APK is only needed when the native side changes
(`ShellPlugin.java`, Capacitor plugins, manifest). The web UI feature-detects
native features (`Capacitor.isPluginAvailable`), so a newer server still works
in an older app.

Mention this in the fdroiddata merge request: the app downloads its UI from the
user's own server, not from a third party.

## First submission

1. Do one release as above (`v1.0.1`).
2. Fork https://gitlab.com/fdroid/fdroiddata and copy
   `docs/fdroid/david.home.ventory.yml` to `metadata/david.home.ventory.yml`.
   Fill in the tag's commit hash (`git rev-parse v1.0.1`) and the fingerprint.
3. Optional, recommended: test it locally with fdroidserver
   (`fdroid readmeta`, `fdroid lint david.home.ventory`,
   `fdroid build -v -l david.home.ventory`).
4. Open a merge request on fdroiddata using the "App inclusion" template.
   Mention that the build is reproducible and the APK is on GitHub releases.

## Check reproducibility yourself

Build twice from clean checkouts in different directories. The
`app-release-unsigned.apk` files must be byte-identical (`sha256sum`). Then
check your signed APK the way F-Droid does:

```sh
apksigcopier compare app-release.apk --unsigned app-release-unsigned.apk
```

If F-Droid reports a mismatch, download both APKs and run `diffoscope` on them.
Usual causes are a different Node or JDK version, or building from a dirty tree.
