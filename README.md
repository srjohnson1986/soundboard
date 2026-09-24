# Soundboard

A communication board for Android. Tap a tile to play a recorded clip or to
have it speak its words aloud with the device's text-to-speech voice.

- **Tiles:** record straight from the microphone, pick an audio file, or type
  what the tile should say.
- **Pages and a home page:** group tiles into pages. The board can return to
  its home page on its own, and keep the home page's first row pinned above
  every other page for things that must always be within reach.
- **Speak...:** type any phrase no tile covers and have it read aloud.
- **Legible labels:** each page's labels size themselves to fit its tiles,
  with two easy-to-read fonts bundled.
- **Saved boards and backups:** save a whole board to come back to later,
  switch between boards, or export everything (sounds included) to a zip.

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
- **[Built-in boards](presets/README.md):** the boards that ship inside the
  app, and how they're made.

The [wiki](https://github.com/srjohnson1986/soundboard/wiki) mirrors the user
guide, architecture and releasing docs for anyone browsing there. `docs/` is
the source of truth: update the relevant doc in the same change as the
feature, and `scripts/sync-wiki.sh` copies it to the wiki at release time.

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

That runs the unit tests: the model, repositories and view model, on the JVM
with Robolectric.

```bash
./gradlew connectedDebugAndroidTest
```

That runs the Compose UI tests on a connected device or emulator.

GitHub Actions runs lint, the unit tests and a debug build on every pull
request, plus the UI tests on an emulator (advisory; emulators occasionally
flake). Pull requests that only change Markdown skip both.

## Work on it

Changes go through a GitHub issue, a feature branch and a pull request
against `master`. See [Architecture](docs/ARCHITECTURE.md) before touching the
code, and keep `docs/` current in the same pull request.
