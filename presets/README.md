# Test presets

The app doesn't have a separate "preset" concept — a preset is just a backup
zip in the format `BoardRepository.exportTo()`/`importFrom()` produces:
`board.json` at the zip root plus every referenced sound under `sounds/`.
Anything built to that shape can be loaded with the app's **Import** button.

## `care-board.zip`

A 5-column x 10-row (50 tile) board built from a batch of recorded clips,
grouped into rows by category (top to bottom): urgent/medical, everyday
needs, yes/no/basic answers, social, "Get Mom" / "Get Dad" contact tiles,
and a single chime.

**It auto-loads in debug builds.** A copy lives at
`app/src/debug/assets/care-board.zip` (keep it in sync with this one), and
`BoardViewModel` imports it on a fresh install — whenever `board.json`
doesn't exist yet, i.e. before the app has ever saved anything. Release
builds carry no such asset, so `repo.importFromAsset()` silently no-ops
there and the app starts with the normal empty 4x4 board. Once *any* board
gets saved — including this auto-import — it's never triggered again;
uninstall (or clear app data) to see it re-trigger.

To load it by hand instead (e.g. onto a build that already has a saved
board):

1. Get the zip onto the device/emulator, e.g. `adb push presets/care-board.zip /sdcard/Download/`.
2. In the app, tap **Import** in the top bar and pick the file.

**Heads up:** Import fully replaces the current board — export first if you
want to keep what's there.
