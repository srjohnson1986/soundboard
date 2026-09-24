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

1. Bump `versionName` (and `versionCode`) in `app/build.gradle.kts`, through
   the usual issue/branch/PR flow. The in-app version label (`APP_VERSION` in
   `ui/BoardTopBar.kt`) and its release-notes link read `versionName` via
   `BuildConfig`, so there's nothing else to keep in sync.
2. Once that's merged, tag the merge commit and push the tag:
   ```bash
   git tag -a vX.Y.Z <commit> -m "vX.Y.Z"
   git push origin vX.Y.Z
   ```
3. Build the signed APK:
   ```bash
   ./gradlew assembleRelease
   ```
   The output lands at `app/build/outputs/apk/release/app-release.apk`.
4. Publish the release and attach the APK:
   ```bash
   gh release create vX.Y.Z --title vX.Y.Z --generate-notes \
     app/build/outputs/apk/release/app-release.apk
   ```
   (Or `gh release upload vX.Y.Z app-release.apk` if the release already
   exists without it.)
