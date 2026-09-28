# Cutting a release

A release publishes the Android APK and the web version. iOS isn't released yet: it's
waiting on an Apple Developer Program membership (docs/IOS.md, "Share it through
TestFlight").

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
what happens on a fresh clone. Locally it's only needed for the update check
in step 2 below; the published APK is built by CI (next section).

## One-time: let CI sign releases

The **Publish Android app** workflow (`.github/workflows/android-release.yml`)
builds the published APK and signs it with the same keystore, which it reads
from four repository secrets. It writes its own `keystore.properties` from
them for the build and deletes both afterwards. Add them from the repo root
(each `gh secret set` without a value prompts for it, so the password never
lands in your shell history):

```bash
base64 -w0 ~/.android/soundboard-release.jks | gh secret set RELEASE_KEYSTORE_BASE64
gh secret set RELEASE_STORE_PASSWORD
gh secret set RELEASE_KEY_ALIAS
gh secret set RELEASE_KEY_PASSWORD
```

(On macOS, `base64 -i ~/.android/soundboard-release.jks` instead.) The
values are the same ones in your `keystore.properties`. Without them the
workflow stops rather than publish an unsigned APK.

It also refuses to publish an APK whose signing certificate isn't the one
every release so far has used (`RELEASE_CERT_SHA256` in the workflow), since
phones that already have the app couldn't install it as an update. If the key
ever genuinely has to change, update that value in the same PR, and expect
everyone to uninstall and reinstall.

To check the secrets work without publishing anything: **Actions → Publish
Android app → Run workflow**, enter an existing tag (e.g. the latest
release's) and leave **publish** unticked. It builds and verifies that tag and
keeps the APK as a workflow artifact.

## Every release

1. Check the docs against the new build, and land any fixes before tagging
   like any other change:
   - **`docs/USER_GUIDE.md`**: menu names, where each setting lives, new
     features.
   - **`README.md`**: the feature summary, install link, build and test
     commands.
   - **`presets/README.md`**: if a built-in board changed, its description,
     and that the bundled copies still match (see its "Keeping the bundled
     copies in sync" section, which has a one-line hash check).
   - **`docs/ARCHITECTURE.md`**: anything the release restructured.
2. **Check an update keeps everything.** Test builds are installed fresh, so
   nothing else exercises updating over the version people already have
   (#247). On an emulator, not a phone someone relies on:
   1. Uninstall any debug build (it's signed with a different key), then
      install the previous release's APK. `soundboard.apk` at the repo root is
      the last one published; otherwise download it from its GitHub release.
   2. Make it look used: rename the board, edit a tile's name, **Save board
      as...** (so there's a saved and a recent board), change a board setting
      (e.g. the theme) and a device one (e.g. Show mode or Performance mode).
   3. Build this release, `./gradlew assembleRelease`, and install it **over**
      the old one without clearing data:
      `adb install -r app/build/outputs/apk/release/app-release.apk`.
   4. Open it and check all of that is still there, the saved board under
      **Switch board**, and no tile newly showing "needs recording" (load drops
      a clip whose file is missing, so that would mean lost recordings).
   Only then tag the release.
3. Bump `appVersionName` (and `appVersionCode`) in `gradle.properties`,
   through the usual issue/branch/PR flow. The Android build reads them for
   `versionName`/`versionCode`, and the in-app version label (`APP_VERSION` in
   `shared/.../ui/BoardTopBar.kt`) and its release-notes link read the same
   value, so there's nothing else to keep in sync.
4. Once that's merged, tag the merge commit and push the tag:
   ```bash
   git tag -a vX.Y.Z <commit> -m "vX.Y.Z"
   git push origin vX.Y.Z
   ```
   Pushing the tag publishes both versions from the tagged commit, so the site
   and the APK are always the same version:
   - **Publish Android app** (`.github/workflows/android-release.yml`) checks
     the tag matches `appVersionName`, builds the release APK signed with the
     release key (see "One-time: let CI sign releases"), checks the signing
     certificate, and publishes the GitHub release with generated notes and the
     APK attached as `soundboard.apk`. Gradle always names its output
     `app-release.apk`; every release publishes it as `soundboard.apk` instead.
     That name is easy to recognize in a phone's Downloads, and because it
     never changes, this link always serves the newest release, which is handy
     to bookmark on the devices that sideload it:
     `https://github.com/srjohnson1986/soundboard/releases/latest/download/soundboard.apk`.
   - **Publish web app** (`.github/workflows/web.yml`) builds the web version,
     smoke-tests it (`web/smoke/smoke.mjs`: from a `/soundboard/` subpath,
     online and offline) and deploys it to
     https://srjohnson1986.github.io/soundboard/. A failed smoke test stops the
     deploy. The `github-pages` environment only accepts deploys from `master`
     and `v*` tags (Settings → Environments → github-pages); a deploy "rejected
     by environment protection rules" means the tag doesn't match those.
5. Check both workflows went green, the release page has `soundboard.apk`, and
   the site's menu shows the new version. **Run workflow** republishes either
   one without a new tag (for the Android one, enter the tag and tick
   **publish**).

   If the Android workflow can't run (e.g. GitHub Actions is down), the manual
   fallback is the same build on your machine, with `keystore.properties` set up:
   ```bash
   ./gradlew assembleRelease
   cp app/build/outputs/apk/release/app-release.apk soundboard.apk
   gh release create vX.Y.Z --title vX.Y.Z --generate-notes soundboard.apk
   ```
   (Or `gh release upload vX.Y.Z soundboard.apk --clobber` if the release
   already exists. `soundboard.apk` at the repo root is gitignored.)
6. Sync the wiki, which mirrors `docs/USER_GUIDE.md`, `docs/ARCHITECTURE.md`,
   `docs/IOS.md` and this file for people who browse the wiki instead of the repo:
   ```bash
   scripts/sync-wiki.sh
   ```
   It copies those files to the wiki's User-Guide, Architecture, Releasing
   and iOS pages, pointing their `../` repo links at GitHub, and only pushes if
   something changed (`--dry-run` shows the diff without pushing). Edit the
   docs here, never the wiki pages directly; the next sync overwrites them.
   The wiki's Home page is the one page kept by hand.
