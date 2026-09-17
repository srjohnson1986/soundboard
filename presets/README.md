# Test presets

The app doesn't have a separate "preset" concept — a preset is just a backup
zip in the format `BoardRepository.exportTo()`/`importFrom()` produces:
`board.json` at the zip root plus every referenced sound under `sounds/`.
Anything built to that shape can be loaded with the app's **Import** button.

## `care-board.zip`

A 5-column x 10-row (50 tile) board built from a batch of recorded clips,
grouped into rows by category (top to bottom): urgent/medical, everyday
needs, yes/no/basic answers, social, two "get \_\_\_" contact tiles, and a
single chime.

To load it:

1. Get the zip onto the device/emulator, e.g. `adb push presets/care-board.zip /sdcard/Download/`.
2. In the app, tap **Import** in the top bar and pick the file.

**Heads up:** Import fully replaces the current board — export first if you
want to keep what's there.

**Known placeholder:** `Get —` and `Get — 2` (originally `get_NAME.wav` /
`get_NAME2.wav`) are stand-ins — the recordings say a placeholder rather
than an actual name. Re-record those two before relying on this board for
real use.
