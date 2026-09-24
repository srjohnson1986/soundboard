# Cutting a release

This app is sideloaded onto specific devices rather than distributed through
the Play Store, so a self-signed key is enough — there's no Play App
Signing enrollment or upload key to manage.

## One-time: generate a release keystore

Only needs doing once, ever, per signing identity. **Losing this file or its
password means every future release needs a brand-new key, and anyone who
already installed a build signed with the old key can't be upgraded
in-place** — Android refuses to install an update signed by a different key
over an existing app. Store it somewhere durable outside the repo (e.g.
`~/.android/`, alongside the debug keystore Android Studio already keeps
there) and keep the password in a password manager, not in a text file next
to the keystore.

```bash
keytool -genkeypair -v \
  -keystore ~/.android/soundboard-release.jks \
  -alias soundboard-release \
  -keyalg RSA -keysize 2048 -validity 10000
```

`keytool` ships with any JDK — Android Studio bundles one at
`<Android Studio install>/jbr/bin/keytool`. `-validity 10000` is about 27
years; there's no reason to make a signing key expire sooner deliberately.

## One-time per machine: point the build at it

Copy [`keystore.properties.example`](../keystore.properties.example) to
`keystore.properties` at the repo root and fill in the real path and
passwords:

```properties
storeFile=/absolute/path/to/soundboard-release.jks
storePassword=...
keyAlias=soundboard-release
keyPassword=...
```

`keystore.properties` is gitignored — it never gets committed, and
`app/build.gradle.kts` treats it as optional: without it, `assembleRelease`
still builds an (unsigned) release APK exactly as it always did, which is
what happens on a fresh clone or in CI (CI only ever runs `assembleDebug`,
never `assembleRelease`, so it doesn't need this file at all).

## Every release

1. Read through `docs/USER_GUIDE.md` with the new build in hand and fix
   anything that no longer matches the app (menu names, where a setting
   lives, new features). Land any fixes before tagging, like any other change.
2. Bump `versionName` (and `versionCode`) in `app/build.gradle.kts`, through
   the usual issue/branch/PR flow. The in-app version label (`APP_VERSION` in
   `ui/BoardTopBar.kt`) and its release-notes link read `versionName` via
   `BuildConfig`, so there's nothing else to keep in sync.
3. Once that's merged, tag the merge commit and push the tag:
   ```bash
   git tag -a vX.Y.Z <commit> -m "vX.Y.Z"
   git push origin vX.Y.Z
   ```
4. Build the signed APK and give it its release name:
   ```bash
   ./gradlew assembleRelease
   cp app/build/outputs/apk/release/app-release.apk soundboard.apk
   ```
   Gradle always names its output `app-release.apk`; every release publishes it
   as `soundboard.apk` instead. That name is easy to recognize in a phone's
   Downloads, and because it never changes, this link always serves the newest
   release, which is handy to bookmark on the devices that sideload it:
   `https://github.com/srjohnson1986/soundboard/releases/latest/download/soundboard.apk`.
   (`soundboard.apk` at the repo root is gitignored.)
5. Publish the release and attach the APK:
   ```bash
   gh release create vX.Y.Z --title vX.Y.Z --generate-notes soundboard.apk
   ```
   (Or `gh release upload vX.Y.Z soundboard.apk` if the release already
   exists without it.)
6. Sync the wiki, which mirrors `docs/USER_GUIDE.md`, `docs/ARCHITECTURE.md`
   and this file for people who browse the wiki instead of the repo:
   ```bash
   scripts/sync-wiki.sh
   ```
   It copies those files to the wiki's User-Guide, Architecture and Releasing
   pages, pointing their `../` repo links at GitHub, and only pushes if
   something changed (`--dry-run` shows the diff without pushing). Edit the
   docs here, never the wiki pages directly; the next sync overwrites them.
   The wiki's Home page is the one page kept by hand.
