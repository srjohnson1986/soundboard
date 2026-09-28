# Soundboard

A communication board for Android and the web, with iOS built and waiting (see
[iOS](#ios)). Tap a tile to play a recorded clip or to have it speak its words
aloud with the device's text-to-speech voice. All three are built from one Kotlin
codebase, so a feature added once reaches every platform.

- **Tiles:** record straight from the microphone, pick an audio file, or type
  what the tile should say.
- **Pages and a home page:** group tiles into pages. The board can return to
  its home page on its own, and keep the home page's first row pinned above
  every other page for things that must always be within reach.
- **Speak...:** type any phrase no tile covers and have it read aloud.
- **Voice:** choose the voice speaking tiles use, from those on the device that
  work offline, and its speed and pitch.
- **Show mode:** a tapped tile's words also fill the screen in large white text
  on black, for someone to read, with the option to mute the sound.
- **Legible labels:** each page's labels size themselves to fit its tiles,
  with two easy-to-read fonts bundled.
- **Saved boards and backups:** save a whole board to come back to later,
  switch between boards, or export everything (sounds included) to a zip. The
  board reminds you when its changes have gone a week without a backup.
- **Caregiver lock:** hide everything that changes the board, so it can't be
  changed by accident; hold Unlock for 3 seconds to get it back.
- **Never silently silent:** if the media volume is off or the device's speech
  isn't working, the board says so instead of just not making a sound.

It's made to be sideloaded onto a particular device, not published on the Play
Store. The only permission it asks for is the microphone, and only the first
time you record.

## Install

Download [**soundboard.apk**](https://github.com/srjohnson1986/soundboard/releases/latest/download/soundboard.apk)
(always the newest release; see [all releases](https://github.com/srjohnson1986/soundboard/releases))
and open it on the device. Android will ask you to allow installs from your
browser or file manager the first time.

## Docs

- **[User guide](docs/USER_GUIDE.md):** how to use the app: tiles and recording,
  speaking, pages and the home page, settings, saving and opening boards, and
  backup.
- **[Architecture](docs/ARCHITECTURE.md):** how the code is put together:
  layers, the data model, persistence, audio, the single write path, and known
  limitations. Read this before making changes.
- **[Releasing](docs/RELEASING.md):** the release checklist: signing, the
  `soundboard.apk` asset, and syncing the wiki.
- **[iOS](docs/IOS.md):** where the iOS app stands, and how to build, run and ship it.
- **[AGENTS.md](AGENTS.md):** the short version for AI assistants (and people): where
  code goes, keeping the platforms in step, commands, and pitfalls.
- **[Built-in boards](presets/README.md):** the boards that ship inside the
  app, and how they're made.

The [wiki](https://github.com/srjohnson1986/soundboard/wiki) mirrors the user
guide, architecture, releasing and iOS docs for anyone browsing there. `docs/` is
the source of truth: update the relevant doc in the same change as the
feature, and `scripts/sync-wiki.sh` copies it to the wiki at release time.

## Web version

**[Try it in your browser](https://srjohnson1986.github.io/soundboard/)**: the same
app, built from the same code (see [Architecture](docs/ARCHITECTURE.md), "Modules"),
nothing to install. It starts with the text-to-speech board, keeps everything in
the browser's own storage, and reads and writes the same backup zips as the
Android app, recordings included. It can be installed like an app (the browser's
**Install** or **Add to Home Screen**) and works offline after the first visit.
The site is republished with every release. To
run it locally instead:

```bash
./gradlew :web:wasmJsBrowserDevelopmentRun
```

That serves it at http://localhost:8080. It needs a current browser: Chrome, Edge
or Firefox, or Safari 18.2 or later.

## iOS

The iOS app is built from the same code and checked in CI on every change: it compiles,
its tests pass on the iPhone simulator, and it launches and draws the board. **It's
waiting on a contributor with an Apple Developer Program membership** to put it on
TestFlight, and with a Mac to try it by hand first (a free Apple ID is enough for that).
[docs/IOS.md](docs/IOS.md) has what's checked and what isn't, how to build and run it,
and how to ship it.

## Build it

Requires Android Studio (or a JDK 17+ and the Android SDK). The app targets
Android 7.0 (API 24) and up.

1. Android Studio → **Open**, and point it at this folder.
2. Let Gradle sync, then run the `app` configuration on a device or emulator.

From the command line:

```bash
./gradlew assembleDebug
```

## Tests

```bash
./gradlew testDebugUnitTest
```

That runs the app's unit tests: the repositories and view model, on the JVM
with Robolectric.

```bash
./gradlew :shared:allTests
```

That runs the tests in the `shared` module, which holds everything the platforms
share: the model, storage, view model and UI. They run on the JVM and compiled to
WebAssembly in headless Chrome (which needs Chrome installed), and on a Mac also on
the iPhone simulator.

```bash
./gradlew connectedDebugAndroidTest
```

That runs the Compose UI tests on a connected device or emulator.

```bash
./gradlew :koverHtmlReportUnit :verifyCoreCoverage
```

That measures the JVM tests' coverage (report in `build/reports/kover/htmlUnit`) and
fails if the core (`data`, `model`, `BoardViewModel`) drops below 90%.

GitHub Actions runs lint, the unit tests, the browser tests, a debug build and a
coverage check on every pull request, split into three jobs that run side by side.
Two advisory workflows run beside them: the UI tests on an Android emulator
(emulators occasionally flake), and **iOS** on a macOS runner, which builds the
shared code and the iOS app, runs the tests on the iPhone simulator, and launches the
app and screenshots it. Pull
requests that only change Markdown skip them. Gradle's build cache, parallel mode and configuration
cache are on (`gradle.properties`), so repeat builds, locally and in CI, only
redo what changed.

## Work on it

Changes go through a GitHub issue, a feature branch and a pull request
against `master`. See [Architecture](docs/ARCHITECTURE.md) before touching the
code, and keep `docs/` current in the same pull request. [AGENTS.md](AGENTS.md)
sums up how to keep Android, the web and iOS in step, and the pull request template
has a checklist for it.

A "Protect master" ruleset enforces that workflow:
- `master` can't be deleted or force-pushed.
- Changes reach it only through a pull request; no approval is needed.
- The CI `build-and-test` check must pass before merging. It passes when all
  of CI's jobs (`android`, `web` and `coverage`) passed, or when a pull request
  only changes Markdown, which skips them.
- Repository admins can bypass the rules in an emergency.
